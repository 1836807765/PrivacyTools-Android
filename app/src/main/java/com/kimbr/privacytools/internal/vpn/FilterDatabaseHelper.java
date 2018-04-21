//package com.kimbr.privacytools.internal.vpn;
//
//import android.content.Context;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
//
//public class FilterDatabaseHelper extends SQLiteOpenHelper {
//
//    private static volatile FilterDatabaseHelper instance;
//
//    private static final String DB_NAME = "PrivacyTools";
//    private static final int DB_VERSION = 1;
//
//    private FilterDatabaseHelper(Context context) {
//        super(context, DB_NAME, null, DB_VERSION);
//    }
//
//    public static FilterDatabaseHelper getInstance(Context context) {
//        if (instance == null) {
//            synchronized (FilterDatabaseHelper.class) {
//                if (instance == null) instance = new FilterDatabaseHelper(context);
//            }
//        }
//
//        return instance;
//    }
//
//    @Override
//    public void onCreate(SQLiteDatabase db) {
//
//    }
//
//    @Override
//    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//
//    }
//}
