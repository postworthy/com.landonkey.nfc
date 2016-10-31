package com.landonkey.nfc;

import android.nfc.Tag;

/**
 * Created by landonkey on 10/29/16.
 */

public class TagClone {
    //private Tag _tag;
    public byte[] UID;
    public TagClone(Tag tag){
        //_tag = tag;
        UID = tag.getId();
    }
    @Override
    public String toString(){
        return "UID = 0x" + byteArrayToHex(UID);
    }

    private static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

}
