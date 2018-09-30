package com.masanari.wallet;

import android.content.Context;

import com.masanari.wallet.hd.HD_WalletFactory;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.params.MainNetParams;

import java.io.IOException;
import java.math.BigInteger;

public class MasanariWallet {

    private static NetworkParameters networkParams = null;

    public final static int MASANARI_ACCOUNT = 0;
    public final static int MIXING_ACCOUNT = 1;
//    public final static int PUBLIC_ACCOUNT = 2;

    public final static int NB_ACCOUNTS = 2;

    public static final BigInteger bDust = BigInteger.valueOf(Coin.parseCoin("0.00000546").longValue());    // https://github.com/bitcoin/bitcoin/pull/2760

    /* Developer fees */
    public static boolean enableMasanariFeeViaBIP47 = false;
    public static final BigInteger bFee = BigInteger.valueOf(Coin.parseCoin("0").longValue()); // default 0.00015
    public final static BigInteger masanariFeeAmountV1 = BigInteger.valueOf(0L); // default: 200000L
    public final static BigInteger masanariFeeAmountV2 = BigInteger.valueOf(0L); // default: 200000L

    public static final long RBF_SEQUENCE_NO = 0xffffffff - 2;

    private static MasanariWallet instance = null;

    private static int currentSelectedAccount = 0;
    private static boolean showTotalBalance = false;

    private MasanariWallet()    { ; }

    public static MasanariWallet getInstance()  {

        if(instance == null)    {
            instance = new MasanariWallet();
        }

        return instance;
    }

    public void setCurrentSelectedAccount(int account) {
        currentSelectedAccount = account;
    }

    public int getCurrentSelectedAccount() {
        return currentSelectedAccount;
    }

    public void setShowTotalBalance(boolean show) {
        showTotalBalance = show;
    }

    public boolean getShowTotalBalance() {
        return showTotalBalance;
    }

    public boolean hasPassphrase(Context ctx)	{
        String passphrase = null;
        try {
            passphrase = HD_WalletFactory.getInstance(ctx).get().getPassphrase();
        }
        catch(IOException ioe) {
            ioe.printStackTrace();
        }
        catch(MnemonicException.MnemonicLengthException mle) {
            mle.printStackTrace();
        }

        if(passphrase != null && passphrase.length() > 0)	{
            return true;
        }
        else	{
            return false;
        }
    }

    public NetworkParameters getCurrentNetworkParams() {
        return (networkParams == null) ? MainNetParams.get() : networkParams;
    }

    public void setCurrentNetworkParams(NetworkParameters params) {
        networkParams = params;
    }

    public boolean isTestNet()  {
        return (networkParams == null) ? false : !(getCurrentNetworkParams() instanceof MainNetParams);
    }

}
