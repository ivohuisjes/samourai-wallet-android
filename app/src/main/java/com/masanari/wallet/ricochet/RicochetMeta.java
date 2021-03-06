package com.masanari.wallet.ricochet;

import android.content.Context;
//import android.util.Log;

import com.masanari.wallet.MasanariWallet;
import com.masanari.wallet.api.APIFactory;
import com.masanari.wallet.bip47.BIP47Meta;
import com.masanari.wallet.bip47.BIP47Util;
import com.masanari.wallet.bip47.SendNotifTxFactory;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.hd.HD_Address;
import com.masanari.wallet.segwit.BIP84Util;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import com.masanari.wallet.send.FeeUtil;
import com.masanari.wallet.send.MyTransactionOutPoint;
import com.masanari.wallet.send.SendFactory;
import com.masanari.wallet.send.UTXO;
import com.masanari.wallet.util.AddressFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

public class RicochetMeta {

    private final static String MASANARI_RICOCHET_TX_FEE_ADDRESS = "bc1qkymumss6zj0rxy9l3v5vqxqwwffy8jjsw3c9cm";
    private final static String TESTNET_MASANARI_RICOCHET_TX_FEE_ADDRESS = "tb1qkymumss6zj0rxy9l3v5vqxqwwffy8jjsyhrkrg";

    private final static int RICOCHET_ACCOUNT = Integer.MAX_VALUE;

    public final static BigInteger masanariFeeAmountV1 = BigInteger.valueOf(200000L);
    public final static BigInteger masanariFeeAmountV2 = BigInteger.valueOf(200000L);

    private static RicochetMeta instance = null;

    private static int index = 0;
    private static LinkedList<JSONObject> fifo = null;
    private static JSONObject lastRicochet = null;

    private static Context context = null;

    private RicochetMeta() { ; }

    public static RicochetMeta getInstance(Context ctx) {

        context = ctx;

        if(instance == null) {
            fifo = new LinkedList<JSONObject>();

            instance = new RicochetMeta();
        }

        return instance;
    }

    public Iterator<JSONObject> getIterator() {
        return fifo.iterator();
    }

    public LinkedList<JSONObject> getQueue() {
        return fifo;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        RicochetMeta.index = index;
    }

    public void add(JSONObject jObj)   {
        fifo.add(jObj);
    }

    public JSONObject peek()   {
        if(!fifo.isEmpty())    {
            return fifo.peek();
        }
        else    {
            return null;
        }
    }

    public JSONObject get(int pos)   {
        if(!fifo.isEmpty() && pos < fifo.size())    {
            return fifo.get(pos);
        }
        else    {
            return null;
        }
    }

    public JSONObject remove()   {
        if(!fifo.isEmpty())    {
            return fifo.remove();
        }
        else    {
            return null;
        }
    }

    public void empty()   {
        fifo.clear();
    }

    public int size()   {
        return fifo.size();
    }

    public JSONObject getLastRicochet() {
        return lastRicochet;
    }

    public void setLastRicochet(JSONObject lastRicochet) {
        RicochetMeta.lastRicochet = lastRicochet;
    }

    public int getRicochetAccount() {
        return RICOCHET_ACCOUNT;
    }

    public JSONObject toJSON() {

        JSONObject jsonPayload = new JSONObject();
        try {

            jsonPayload.put("index", index);

            if(lastRicochet != null)    {
                jsonPayload.put("last_ricochet", lastRicochet);
            }

            JSONArray array = new JSONArray();
            Iterator<JSONObject> itr = getIterator();
            while(itr.hasNext()){
                JSONObject obj = itr.next();
                array.put(obj);
            }
            jsonPayload.put("queue", array);

        }
        catch(JSONException je) {
            ;
        }

//        Log.i("RicochetMeta", jsonPayload.toString());

        return jsonPayload;
    }

    public void fromJSON(JSONObject jsonPayload) {

//        Log.i("RicochetMeta", jsonPayload.toString());

        try {

            if(jsonPayload.has("index"))    {
                index = jsonPayload.getInt("index");
            }
            if(jsonPayload.has("last_ricochet"))    {
                lastRicochet = jsonPayload.getJSONObject("last_ricochet");
            }
            if(jsonPayload.has("queue"))    {

                fifo.clear();

                JSONArray array = jsonPayload.getJSONArray("queue");
                for(int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    fifo.add(obj);
                }

            }

        }
        catch(JSONException ex) {
            throw new RuntimeException(ex);
        }

    }

    public JSONObject script(long spendAmount, long feePerKBAmount, String strDestination, int nbHops, String strPCode, boolean masanariFeeViaBIP47) {

        JSONObject jObj = new JSONObject();

        try {

            BigInteger biSpend = BigInteger.valueOf(spendAmount);
            BigInteger biMasanariFee = BigInteger.valueOf(masanariFeeAmountV2.longValue() * ((nbHops - 4) + 1));    // 4 hops min. for base fee, each additional hop 0.001
            BigInteger biFeePerKB = BigInteger.valueOf(feePerKBAmount);

            jObj.put("ts", System.currentTimeMillis() / 1000L);
            jObj.put("hops", nbHops);
            jObj.put("spend_amount", biSpend.longValue());
            jObj.put("masanari_fee", biMasanariFee.longValue());
            jObj.put("masanari_fee_via_bip47", masanariFeeViaBIP47);
            jObj.put("feeKB", biFeePerKB.longValue());
            jObj.put("destination", strDestination);
            if(strPCode != null)    {
                jObj.put("pcode", strPCode);
            }

            JSONObject jHop = new JSONObject();
            JSONArray jHops = new JSONArray();

            int hopSz = 0;
            if(masanariFeeViaBIP47)    {
                hopSz = FeeUtil.getInstance().estimatedSize(1, 2);
            }
            else    {
                hopSz = FeeUtil.getInstance().estimatedSize(1, 1);
            }
            BigInteger biFeePerHop = FeeUtil.getInstance().calculateFee(hopSz, biFeePerKB);

            Pair<List<UTXO>, BigInteger> pair = getHop0UTXO(spendAmount, nbHops, biFeePerHop.longValue(), masanariFeeViaBIP47);
            List<UTXO> utxos = pair.getLeft();
            long totalValueSelected = 0L;
            for(UTXO u : utxos)   {
                totalValueSelected += u.getValue();
            }
//            Log.d("RicochetMeta", "totalValueSelected (return):" + totalValueSelected);

            // hop0 'leaves' wallet, change returned to wallet
            BigInteger hop0 = biSpend.add(biMasanariFee).add(biFeePerHop.multiply(BigInteger.valueOf((long) nbHops)));

            //            BigInteger hop0Fee = FeeUtil.getInstance().calculateFee(hop0sz, biFeePerKB);
            BigInteger hop0Fee = pair.getRight();
//            Log.d("RicochetMeta", "hop0Fee (return):" + hop0Fee.longValue());

            Transaction txHop0 = getHop0Tx(utxos, hop0.longValue(), getDestinationAddress(index), hop0Fee.longValue(), masanariFeeViaBIP47);
            if(txHop0 == null)    {
                return null;
            }

//            Log.d("RicochetMeta", "searching for:" + getDestinationAddress(index));
            int prevTxN = 0;
            for(int i = 0; i < txHop0.getOutputs().size(); i++)   {
                Script script = txHop0.getOutputs().get(i).getScriptPubKey();
//                Log.d("RicochetMeta", "script:" + Hex.toHexString(script.getProgram()));
                String address = null;
                if(Hex.toHexString(script.getProgram()).startsWith("0014"))    {
                    String hrp = null;
                    if(MasanariWallet.getInstance().getCurrentNetworkParams() instanceof TestNet3Params)    {
                        hrp = "tb";
                    }
                    else    {
                        hrp = "bc";
                    }
                    try {
                        String _script = Hex.toHexString(script.getProgram());
                        address = Bech32Segwit.encode(hrp, (byte)0x00, Hex.decode(_script.substring(4).getBytes()));
                    }
                    catch(Exception e) {
                        ;
                    }
//                    Log.d("RicochetMeta", "bech32:" + address);
                }
                else    {
                    address = new Script(script.getProgram()).getToAddress(MasanariWallet.getInstance().getCurrentNetworkParams()).toString();
//                    Log.d("RicochetMeta", "address from script:" + address);
                }
                if(address.equals(getDestinationAddress(index)))    {
                    prevTxN = i;
//                    Log.d("RicochetMeta", "tx output n:" + prevTxN);
                    break;
                }
            }

            jHop.put("seq", 0);
            jHop.put("spend_amount", hop0.longValue());
            jHop.put("fee", hop0Fee.longValue());
            jHop.put("fee_per_hop", biFeePerHop.longValue());
            jHop.put("index", index);
            jHop.put("destination", getDestinationAddress(index));
//            Log.d("RicochetMeta", "destination:" + getDestinationAddress(index));
            int prevIndex = index;
            index++;
            jHop.put("tx", new String(Hex.encode(txHop0.bitcoinSerialize())));
            jHop.put("hash", txHop0.getHash().toString());

            jHops.put(jHop);

            List<Pair<String,Long>> masanariFees = new ArrayList<Pair<String,Long>>();
            if(masanariFeeViaBIP47)    {

                long baseVal = masanariFeeAmountV2.longValue() / 4L;
                long totalVal = 0L;
                SecureRandom random = new SecureRandom();

                int _outgoingIdx = BIP47Meta.getInstance().getOutgoingIdx(BIP47Meta.strMasanariDonationPCode);

                for(int i = 0; i < 4; i++)   {
                    int val = random.nextInt(25000);
                    int sign = random.nextInt(1);
                    if(sign == 0)    {
                        val *= -1L;
                    }
                    long feeVal = 0L;
                    if(i == 3)    {
                        feeVal = masanariFeeAmountV2.longValue() - totalVal;
                    }
                    else    {
                        feeVal = baseVal + val;
                        totalVal += feeVal;
                    }

                    //
                    // put address here
                    //
                    try {
                        PaymentCode pcode = new PaymentCode(BIP47Meta.strMasanariDonationPCode);
                        PaymentAddress paymentAddress = BIP47Util.getInstance(context).getSendAddress(pcode, _outgoingIdx + i);
//                        String strAddress = paymentAddress.getSendECKey().toAddress(MasanariWallet.getInstance().getCurrentNetworkParams()).toString();
                        //
                        // derive as bech32
                        //
                        SegwitAddress segwitAddress = new SegwitAddress(paymentAddress.getSendECKey().getPubKey(), MasanariWallet.getInstance().getCurrentNetworkParams());
                        String strAddress = segwitAddress.getBech32AsString();

                        masanariFees.add(Pair.of(strAddress, feeVal));
//                        masanariFees.add(Pair.of(strAddress, 200000L / 4L));
                    }
                    catch(Exception e) {
                        masanariFees.add(Pair.of(SendNotifTxFactory.MASANARI_NOTIF_TX_FEE_ADDRESS, feeVal));
                    }

                }

            }

            Transaction txHop = null;
            String prevTxHash = txHop0.getHash().toString();
            String prevScriptPubKey = Hex.toHexString(txHop0.getOutput(prevTxN).getScriptPubKey().getProgram());

            BigInteger remainingMasanariFee = BigInteger.ZERO;
            long prevSpendValue = hop0.longValue();
            if(!masanariFeeViaBIP47)    {
                prevSpendValue -= biMasanariFee.longValue();
            }
            else    {
                remainingMasanariFee = masanariFeeAmountV2;
            }
            int _hop = 0;
            for (int i = (nbHops - 1); i >= 0; i--) {
                _hop++;
                BigInteger hopx = null;
                if(masanariFeeViaBIP47)    {
                    remainingMasanariFee = remainingMasanariFee.subtract(BigInteger.valueOf(masanariFees.get(_hop - 1).getRight()));
                    hopx = biSpend.add(biFeePerHop.multiply(BigInteger.valueOf((long) i))).add(remainingMasanariFee);
                }
                else    {
                    hopx = biSpend.add(biFeePerHop.multiply(BigInteger.valueOf((long) i)));
                }

                //                Log.d("RicochetMeta", "doing hop:" + _hop);
                if(masanariFeeViaBIP47 && ((_hop - 1) < 4))    {
                    txHop = getHopTx(prevTxHash, prevTxN, prevIndex, prevSpendValue, hopx.longValue(), _hop < nbHops ? getDestinationAddress(index) : strDestination, masanariFees.get(_hop - 1));
                }
                else    {
                    txHop = getHopTx(prevTxHash, prevTxN, prevIndex, prevSpendValue, hopx.longValue(), _hop < nbHops ? getDestinationAddress(index) : strDestination, null);
                }

                if(txHop == null)    {
                    return null;
                }

                jHop = new JSONObject();
                jHop.put("seq", (nbHops - i));
                jHop.put("spend_amount", hopx.longValue());
                jHop.put("fee", biFeePerHop.longValue());
                jHop.put("prev_tx_hash", prevTxHash);
                jHop.put("prev_tx_n", prevTxN);
                jHop.put("prev_spend_value", prevSpendValue);
                jHop.put("script", prevScriptPubKey);
                jHop.put("tx", new String(Hex.encode(txHop.bitcoinSerialize())));
                jHop.put("hash", txHop.getHash().toString());
                if(_hop < nbHops)    {
                    jHop.put("index", index);
                    jHop.put("destination", getDestinationAddress(index));
//                    Log.d("RicochetMeta", "destination:" + getDestinationAddress(index));
                    prevIndex = index;
                    index++;
                }
                else    {
                    jHop.put("destination", strDestination);
//                    Log.d("RicochetMeta", "destination:" + strDestination);
                }

                if(masanariFeeViaBIP47)    {
                    jObj.put("masanari_fee_address", masanariFees.get(_hop - 1).getLeft());
                    jObj.put("masanari_fee_amount", masanariFees.get(_hop - 1).getRight());
                }

                jHops.put(jHop);

                prevTxHash = txHop.getHash().toString();
                prevTxN = 0;
                prevSpendValue = hopx.longValue();
                prevScriptPubKey = Hex.toHexString(txHop.getOutputs().get(0).getScriptPubKey().getProgram());
            }

            jObj.put("hops", jHops);

            BigInteger totalAmount = hop0.add(hop0Fee);

            jObj.put("total_spend", totalAmount.longValue());

        }
        catch(JSONException je) {
            return null;
        }

        System.out.println("RicochetMeta:" + jObj.toString());

        return jObj;
    }

    private String getDestinationAddress(int idx)    {

        HD_Address hd_addr = BIP84Util.getInstance(context).getWallet().getAccountAt(RICOCHET_ACCOUNT).getChain(AddressFactory.RECEIVE_CHAIN).getAddressAt(idx);
        SegwitAddress segwitAddress = new SegwitAddress(hd_addr.getECKey().getPubKey(), MasanariWallet.getInstance().getCurrentNetworkParams());
        String address = segwitAddress.getBech32AsString();

        return address;
    }

    private Pair<List<UTXO>, BigInteger> getHop0UTXO(long spendAmount, int nbHops, long feePerHop, boolean masanariFeeViaBIP47) {

        List<UTXO> utxos = APIFactory.getInstance(context).getUtxos(true);

        final List<UTXO> selectedUTXO = new ArrayList<UTXO>();
        long totalValueSelected = 0L;
        long totalSpendAmount = 0L;
        int selected = 0;

        // sort in ascending order by value
        Collections.sort(utxos, new UTXO.UTXOComparator());
        Collections.reverse(utxos);

        for(UTXO u : utxos)   {
            selectedUTXO.add(u);
            totalValueSelected += u.getValue();
            selected += u.getOutpoints().size();
//            Log.d("RicochetMeta", "selected:" + u.getValue());

            if(masanariFeeViaBIP47)    {
                totalSpendAmount = spendAmount + masanariFeeAmountV2.longValue() + (feePerHop * nbHops) + MasanariWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFee(selected, 3).longValue();
            }
            else    {
                totalSpendAmount = spendAmount + masanariFeeAmountV1.longValue() + (feePerHop * nbHops) + MasanariWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFee(selected, 3).longValue();
            }
//            Log.d("RicochetMeta", "totalSpendAmount:" + totalSpendAmount);
//            Log.d("RicochetMeta", "totalValueSelected:" + totalValueSelected);
            if(totalValueSelected >= totalSpendAmount)    {
//                Log.d("RicochetMeta", "breaking");
                break;
            }
        }

        if(selectedUTXO.size() < 1)    {
            return Pair.of(null, null);
        }
        else    {
            return Pair.of(selectedUTXO, FeeUtil.getInstance().estimatedFee(selected, 3));
        }
    }

    private Transaction getHop0Tx(List<UTXO> utxos, long spendAmount, String destination, long fee, boolean masanariFeeViaBIP47) {

        List<MyTransactionOutPoint> unspent = new ArrayList<MyTransactionOutPoint>();
        long totalValueSelected = 0L;
        for(UTXO u : utxos)   {
            totalValueSelected += u.getValue();
            unspent.addAll(u.getOutpoints());
        }

//        Log.d("RicochetMeta", "spendAmount:" + spendAmount);
//        Log.d("RicochetMeta", "fee:" + fee);
//        Log.d("RicochetMeta", "totalValueSelected:" + totalValueSelected);

        BigInteger masanariFeeAmount = masanariFeeAmountV2;

        long changeAmount = totalValueSelected - (spendAmount + fee);
//        Log.d("RicochetMeta", "changeAmount:" + changeAmount);
        HashMap<String, BigInteger> receivers = new HashMap<String, BigInteger>();

        if(changeAmount > 0L)    {
            String change_address = BIP84Util.getInstance(context).getAddressAt(AddressFactory.CHANGE_CHAIN, BIP84Util.getInstance(context).getWallet().getAccount(0).getChange().getAddrIdx()).getBech32AsString();
            receivers.put(change_address, BigInteger.valueOf(changeAmount));
        }

        if(masanariFeeViaBIP47)    {
            // Masanari fee paid in the hops
            receivers.put(destination, BigInteger.valueOf(spendAmount));
        }
        else    {
            receivers.put(MasanariWallet.getInstance().isTestNet() ? TESTNET_MASANARI_RICOCHET_TX_FEE_ADDRESS : MASANARI_RICOCHET_TX_FEE_ADDRESS, masanariFeeAmount);
            receivers.put(destination, BigInteger.valueOf(spendAmount - masanariFeeAmount.longValue()));
        }

        Transaction tx = SendFactory.getInstance(context).makeTransaction(0, unspent, receivers);
        tx = SendFactory.getInstance(context).signTransaction(tx);

        return tx;
    }

    private Transaction getHopTx(String prevTxHash, int prevTxN, int prevIndex, long prevSpendAmount, long spendAmount, String destination, Pair<String,Long> masanariFeePair) {

        TransactionOutput output = null;
        if(destination.toLowerCase().startsWith("tb") || destination.toLowerCase().startsWith("bc"))   {

            byte[] bScriptPubKey = null;

            try {
                Pair<Byte, byte[]> pair = Bech32Segwit.decode(MasanariWallet.getInstance().isTestNet() ? "tb" : "bc", destination);
                bScriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
            }
            catch(Exception e) {
                return null;
            }
            output = new TransactionOutput(MasanariWallet.getInstance().getCurrentNetworkParams(), null, Coin.valueOf(spendAmount), bScriptPubKey);
        }
        else    {
            Script outputScript = ScriptBuilder.createOutputScript(org.bitcoinj.core.Address.fromBase58(MasanariWallet.getInstance().getCurrentNetworkParams(), destination));
            output = new TransactionOutput(MasanariWallet.getInstance().getCurrentNetworkParams(), null, Coin.valueOf(spendAmount), outputScript.getProgram());
        }

        HD_Address address = BIP84Util.getInstance(context).getWallet().getAccountAt(RICOCHET_ACCOUNT).getChain(AddressFactory.RECEIVE_CHAIN).getAddressAt(prevIndex);
        ECKey ecKey = address.getECKey();
        SegwitAddress p2wpkh = new SegwitAddress(ecKey.getPubKey(), MasanariWallet.getInstance().getCurrentNetworkParams());
        Script redeemScript = p2wpkh.segWitRedeemScript();

        Transaction tx = new Transaction(MasanariWallet.getInstance().getCurrentNetworkParams());
        tx.addOutput(output);

        if(masanariFeePair != null)    {

            byte[] bScriptPubKey = null;

            try {
                Pair<Byte, byte[]> pair = Bech32Segwit.decode(MasanariWallet.getInstance().isTestNet() ? "tb" : "bc", masanariFeePair.getLeft());
                bScriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
            }
            catch(Exception e) {
                return null;
            }
            TransactionOutput _output = new TransactionOutput(MasanariWallet.getInstance().getCurrentNetworkParams(), null, Coin.valueOf(masanariFeePair.getRight()), bScriptPubKey);
            tx.addOutput(_output);
        }

//        Log.d("RicochetMeta", "spending from:" + p2wpkh.getBech32AsString());
//        Log.d("RicochetMeta", "pubkey:" + Hex.toHexString(ecKey.getPubKey()));

        Sha256Hash txHash = Sha256Hash.wrap(prevTxHash);
        TransactionOutPoint outPoint = new TransactionOutPoint(MasanariWallet.getInstance().getCurrentNetworkParams(), prevTxN, txHash, Coin.valueOf(prevSpendAmount));
        TransactionInput txInput = new TransactionInput(MasanariWallet.getInstance().getCurrentNetworkParams(), null, new byte[]{}, outPoint, Coin.valueOf(prevSpendAmount));
        tx.addInput(txInput);

        TransactionSignature sig = tx.calculateWitnessSignature(0, ecKey, redeemScript.scriptCode(), Coin.valueOf(prevSpendAmount), Transaction.SigHash.ALL, false);
        final TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, sig.encodeToBitcoin());
        witness.setPush(1, ecKey.getPubKey());
        tx.setWitness(0, witness);

        assert(0 == tx.getInput(0).getScriptBytes().length);
//        Log.d("RicochetMeta", "script sig length:" + tx.getInput(0).getScriptBytes().length);

        tx.verify();

        return tx;
    }

}
