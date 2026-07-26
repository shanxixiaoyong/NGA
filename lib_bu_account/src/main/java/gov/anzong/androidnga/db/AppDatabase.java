package gov.anzong.androidnga.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import gov.anzong.androidnga.db.user.UserDao;
import sp.phone.common.User;

/**
 * @author yangyihang
 */
@Database(entities = {User.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String MAIN_DB_NAME = "app_database.db";

    private static AppDatabase sInstance;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE users ADD COLUMN account_id TEXT");
        }
    };

    public static void init(Context context) {
        sInstance = Room.databaseBuilder(context, AppDatabase.class, MAIN_DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .build();
    }

    public static AppDatabase getInstance() {
        return sInstance;
    }

    public abstract UserDao userDao();

}
