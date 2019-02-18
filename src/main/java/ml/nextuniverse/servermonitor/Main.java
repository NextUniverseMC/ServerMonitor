package ml.nextuniverse.servermonitor;

import com.zaxxer.hikari.HikariDataSource;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Created by TheDiamondPicks on 20/08/2017.
 */
public class Main extends Plugin {

    private static Main instance;
    private static HikariDataSource hikari;

    @Override
    public void onEnable() {
        instance = this;

        try {
            Configuration configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
            hikari = new HikariDataSource();
            hikari.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
            hikari.addDataSourceProperty("serverName", configuration.getString("serverName"));
            hikari.addDataSourceProperty("port",  3306);
            hikari.addDataSourceProperty("databaseName",  configuration.getString("databaseName"));
            hikari.addDataSourceProperty("user",  configuration.getString("user"));
            hikari.addDataSourceProperty("password",  configuration.getString("password"));
        }
        catch (IOException e) {
            getLogger().severe("Could not load config");
            e.printStackTrace();
        }
        createTable();
        final String CPU = "INSERT INTO CPU VALUES(?,?) ON DUPLICATE KEY UPDATE `usage`=?";
        final String deleteCPU = "DELETE FROM CPU WHERE `time` <= ?";
        final String deleteMEM = "DELETE FROM MEMORY WHERE `time` <= ?";
        final String deleteNETWORK = "DELETE FROM NETWORK WHERE `time` <= ?";
        final String MEM = "INSERT INTO MEMORY VALUES(?,?) ON DUPLICATE KEY UPDATE `usage`=?";
        final String NETWORK = "INSERT INTO NETWORK VALUES(?,?) ON DUPLICATE KEY UPDATE `usage`=?";


        getProxy().getScheduler().schedule(this, new Runnable() {
            @Override
            public void run() {
                ProxyServer.getInstance().getScheduler().runAsync(Main.getInstance(), new Runnable() {
                    @Override
                    public void run() {
                        try (Connection connection = Main.getHikari().getConnection();
                             PreparedStatement insert = connection.prepareStatement(CPU)) {

                            SystemInfo si = new SystemInfo();
                            CentralProcessor processor = si.getHardware().getProcessor();
                            BigDecimal usage = new BigDecimal(processor.getSystemCpuLoad() * 100);
                            usage = usage.setScale(1, RoundingMode.HALF_UP);

                            insert.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                            insert.setBigDecimal(2, usage);
                            insert.setBigDecimal(3, usage);
                            insert.execute();
                        }
                        catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }, 1, 1, TimeUnit.SECONDS);

        getProxy().getScheduler().schedule(this, new Runnable() {
            @Override
            public void run() {
                ProxyServer.getInstance().getScheduler().runAsync(Main.getInstance(), new Runnable() {
                    @Override
                    public void run() {
                        try (Connection connection = hikari.getConnection();
                             PreparedStatement deleteCpu = connection.prepareStatement(deleteCPU);
                             PreparedStatement deleteMem = connection.prepareStatement(deleteMEM);
                             PreparedStatement deleteNet = connection.prepareStatement(deleteNETWORK)) {

                            Calendar c = Calendar.getInstance();
                            c.setTimeInMillis(System.currentTimeMillis());
                            c.add(Calendar.DAY_OF_YEAR, -1);
                            Timestamp t = new Timestamp(c.getTimeInMillis());

                            deleteNet.setTimestamp(1, t);
                            deleteCpu.setTimestamp(1, t);
                            deleteMem.setTimestamp(1, t);
                            deleteNet.execute();
                            deleteCpu.execute();
                            deleteMem.execute();
                        }
                        catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }, 0, 1, TimeUnit.DAYS);

    }

    private void createTable(){
        try(Connection connection = hikari.getConnection();
            Statement statement = connection.createStatement();){
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS CPU(`time` TIMESTAMP, `usage` DECIMAL(4, 1), PRIMARY KEY (time))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Memory(`time` TIMESTAMP, `usage` DECIMAL(4, 1), PRIMARY KEY (time))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Network(`time` TIMESTAMP, `usage` DECIMAL(4, 1), PRIMARY KEY (time))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static Plugin getInstance() {
        return instance;
    }
    public static HikariDataSource getHikari() {
        return hikari;
    }
}
