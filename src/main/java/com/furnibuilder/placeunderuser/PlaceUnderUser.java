package com.furnibuilder.placeunderuser;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PlaceUnderUser extends HabboPlugin implements EventListener {
  public static PlaceUnderUser INSTANCE = null;
  
  public void onEnable() {
    INSTANCE = this;
    Emulator.getPluginManager().registerEvents(this, this);
    if (Emulator.isReady) {
      checkDatabase();
      ICallable callable2 = new RotateMoveFurniture();
      Emulator.getGameServer().getPacketManager().registerCallable(Integer.valueOf(248), callable2);
      ICallable callable3 = new PlaceFurniture();
      Emulator.getGameServer().getPacketManager().registerCallable(Integer.valueOf(1258), callable3);
    } 
    Emulator.getLogging().logStart("[Pickup] Started Pickup Command Plugin!");
  }
  
  public void onDisable() {
    Emulator.getGameServer().getPacketManager().unregisterCallables(Integer.valueOf(248));
    Emulator.getGameServer().getPacketManager().unregisterCallables(Integer.valueOf(1258));
    Emulator.getLogging().logShutdownLine("[Pickup] Stopped Pickup Command Plugin!");
  }
  
  @EventHandler
  public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
    INSTANCE.checkDatabase();
    ICallable callable2 = new RotateMoveFurniture();
    Emulator.getGameServer().getPacketManager().registerCallable(Integer.valueOf(248), callable2);
    ICallable callable3 = new PlaceFurniture();
    Emulator.getGameServer().getPacketManager().registerCallable(Integer.valueOf(1258), callable3);
  }
  
  public boolean hasPermission(Habbo habbo, String s) {
    return false;
  }
  
  private void checkDatabase() {}
  
  private boolean registerPermission(String name, String options, String defaultValue, boolean defaultReturn) {
    try(Connection connection = Emulator.getDatabase().getDataSource().getConnection(); 
        
        PreparedStatement statement = connection.prepareStatement("ALTER TABLE  `permissions` ADD  `" + name + "` ENUM(  " + options + " ) NOT NULL DEFAULT  '" + defaultValue + "'")) {
      statement.execute();
      return true;
    } catch (SQLException sQLException) {
      return defaultReturn;
    } 
  }
  
  public static void main(String[] args) {
    System.out.println("Don't run this seperately");
  }
}
