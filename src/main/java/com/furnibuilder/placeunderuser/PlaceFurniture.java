package com.furnibuilder.placeunderuser;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionJukeBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionMoodLight;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureBuildheightEvent;
import com.eu.habbo.plugin.events.furniture.FurniturePlacedEvent;
import gnu.trove.iterator.hash.TObjectHashIterator;
import gnu.trove.set.hash.THashSet;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.util.Pair;

public class PlaceFurniture implements ICallable {
  public void call(MessageHandler messageHandler) {
    messageHandler.isCancelled = true;
    String[] values = messageHandler.packet.readString().split(" ");
    int itemId = -1;
    if (values.length != 0)
      itemId = Integer.valueOf(values[0]).intValue(); 
    if (!messageHandler.client.getHabbo().getRoomUnit().isInRoom()) {
      messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
      return;
    } 
    Room room = messageHandler.client.getHabbo().getHabboInfo().getCurrentRoom();
    if (room == null)
      return; 
    HabboItem rentSpace = null;
    if (messageHandler.client.getHabbo().getHabboStats().isRentingSpace())
      rentSpace = room.getHabboItem((messageHandler.client.getHabbo().getHabboStats()).rentedItemId); 
    HabboItem item = messageHandler.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);
    if (item == null || item.getBaseItem().getInteractionType().getType() == InteractionPostIt.class)
      return; 
    if (room.getId() != item.getRoomId() && item.getRoomId() != 0)
      return; 
    if (item instanceof InteractionMoodLight && !room.getRoomSpecialTypes().getItemsOfType(InteractionMoodLight.class).isEmpty()) {
      messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.MAX_DIMMERS.errorCode));
      return;
    } 
    if (item instanceof InteractionJukeBox && !room.getRoomSpecialTypes().getItemsOfType(InteractionJukeBox.class).isEmpty()) {
      messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.MAX_SOUNDFURNI.errorCode));
      return;
    } 
    if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
      short x = Short.valueOf(values[1]).shortValue();
      short y = Short.valueOf(values[2]).shortValue();
      int rotation = Integer.valueOf(values[3]).intValue();
      if (rentSpace != null && !room.hasRights(messageHandler.client.getHabbo())) {
        if (item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionRoller || item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper || item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionWired || item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionBackgroundToner || item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionRoomAds || item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionCannon || item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionPuzzleBox || item
          
          .getBaseItem().getType() == FurnitureType.WALL) {
          messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
          return;
        } 
        if (!RoomLayout.squareInSquare(RoomLayout.getRectangle(rentSpace.getX(), rentSpace.getY(), rentSpace.getBaseItem().getWidth(), rentSpace.getBaseItem().getLength(), rentSpace.getRotation()), RoomLayout.getRectangle(x, y, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation))) {
          messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
          return;
        } 
      } 
      RoomTile tile = room.getLayout().getTile(x, y);
      FurnitureMovementError error = room.canPlaceFurnitureAt(item, messageHandler.client.getHabbo(), tile, rotation);
      if (!error.equals(FurnitureMovementError.NONE)) {
        messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
        return;
      } 
      error = placeFloorFurniAt(room, item, tile, rotation, messageHandler.client.getHabbo());
      if (!error.equals(FurnitureMovementError.NONE)) {
        messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
        return;
      } 
    } else {
      FurnitureMovementError error = room.placeWallFurniAt(item, values[1] + " " + values[2] + " " + values[3], messageHandler.client.getHabbo());
      if (!error.equals(FurnitureMovementError.NONE)) {
        messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
        return;
      } 
    } 
    messageHandler.client.sendResponse((MessageComposer)new RemoveHabboItemComposer(item.getGiftAdjustedId()));
    messageHandler.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item.getId());
    item.setFromGift(false);
  }
  
  public FurnitureMovementError placeFloorFurniAt(Room room, HabboItem item, RoomTile tile, int rotation, Habbo owner) {
    boolean pluginHelper = false;
    if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
      FurniturePlacedEvent event = (FurniturePlacedEvent)Emulator.getPluginManager().fireEvent((Event)new FurniturePlacedEvent(item, owner, tile));
      if (event.isCancelled())
        return FurnitureMovementError.CANCEL_PLUGIN_PLACE; 
      pluginHelper = event.hasPluginHelper();
    } 
    THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
    FurnitureMovementError fits = furnitureFitsAt(room, tile, item, rotation);
    if (!fits.equals(FurnitureMovementError.NONE) && !pluginHelper)
      return fits; 
    double height = tile.getStackHeight();
    if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
      FurnitureBuildheightEvent event = (FurnitureBuildheightEvent)Emulator.getPluginManager().fireEvent((Event)new FurnitureBuildheightEvent(item, owner, 0.0D, height));
      if (event.hasChangedHeight())
        height = event.getUpdatedHeight(); 
    } 
    item.setZ(height);
    item.setX(tile.x);
    item.setY(tile.y);
    item.setRotation(rotation);
    if (!room.getFurniOwnerNames().containsKey(item.getUserId()) && owner != null)
      room.getFurniOwnerNames().put(item.getUserId(), owner.getHabboInfo().getUsername()); 
    item.needsUpdate(true);
    room.addHabboItem(item);
    item.setRoomId(room.getId());
    item.onPlace(room);
    room.updateTiles(occupiedTiles);
    room.sendComposer((new AddFloorItemComposer(item, room.getFurniOwnerName(item.getUserId()))).compose());
    for (TObjectHashIterator<RoomTile> tObjectHashIterator = occupiedTiles.iterator(); tObjectHashIterator.hasNext(); ) {
      RoomTile t = tObjectHashIterator.next();
      room.updateHabbosAt(t.x, t.y);
      room.updateBotsAt(t.x, t.y);
    } 
    Emulator.getThreading().run((Runnable)item);
    return FurnitureMovementError.NONE;
  }
  
  public FurnitureMovementError furnitureFitsAt(Room room, RoomTile tile, HabboItem item, int rotation) {
    if (!room.getLayout().fitsOnMap(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation))
      return FurnitureMovementError.INVALID_MOVE; 
    if (item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper)
      return FurnitureMovementError.NONE; 
    boolean canHaveUser = false;
    if (item.getBaseItem().allowSit() || item.getBaseItem().allowWalk() || item.getBaseItem().allowLay())
      canHaveUser = true; 
    THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
    for (TObjectHashIterator<RoomTile> tObjectHashIterator1 = occupiedTiles.iterator(); tObjectHashIterator1.hasNext(); ) {
      RoomTile t = tObjectHashIterator1.next();
      if (room.hasHabbosAt(t.x, t.y) && !canHaveUser)
        return FurnitureMovementError.TILE_HAS_HABBOS; 
      if (room.hasBotsAt(t.x, t.y))
        return FurnitureMovementError.TILE_HAS_BOTS; 
      if (room.hasPetsAt(t.x, t.y))
        return FurnitureMovementError.TILE_HAS_PETS; 
    } 
    List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new ArrayList<>();
    for (TObjectHashIterator<RoomTile> tObjectHashIterator2 = occupiedTiles.iterator(); tObjectHashIterator2.hasNext(); ) {
      RoomTile t = tObjectHashIterator2.next();
      tileFurniList.add(Pair.create(t, room.getItemsAt(t)));
      HabboItem topItem = room.getTopItemAt(t.x, t.y, item);
      if (topItem != null && !topItem.getBaseItem().allowStack() && !t.getAllowStack())
        return FurnitureMovementError.CANT_STACK; 
    } 
    if (!item.canStackAt(room, tileFurniList))
      return FurnitureMovementError.CANT_STACK; 
    return FurnitureMovementError.NONE;
  }
}
