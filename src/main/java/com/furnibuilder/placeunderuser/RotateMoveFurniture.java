package com.furnibuilder.placeunderuser;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureBuildheightEvent;
import com.eu.habbo.plugin.events.furniture.FurnitureMovedEvent;
import com.eu.habbo.plugin.events.furniture.FurnitureRotatedEvent;
import gnu.trove.iterator.hash.TObjectHashIterator;
import gnu.trove.set.hash.THashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.math3.util.Pair;

public class RotateMoveFurniture implements ICallable {
  public void call(MessageHandler messageHandler) {
    messageHandler.isCancelled = true;
    Room room = messageHandler.client.getHabbo().getHabboInfo().getCurrentRoom();
    if (room == null)
      return; 
    int furniId = messageHandler.packet.readInt().intValue();
    HabboItem item = room.getHabboItem(furniId);
    if (item == null)
      return; 
    int x = messageHandler.packet.readInt().intValue();
    int y = messageHandler.packet.readInt().intValue();
    int rotation = messageHandler.packet.readInt().intValue();
    RoomTile tile = room.getLayout().getTile((short)x, (short)y);
    FurnitureMovementError error = room.canPlaceFurnitureAt(item, messageHandler.client.getHabbo(), tile, rotation);
    if (!error.equals(FurnitureMovementError.NONE)) {
      messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
      messageHandler.client.sendResponse((MessageComposer)new FloorItemUpdateComposer(item));
      return;
    } 
    error = moveFurniTo(room, item, tile, rotation, messageHandler.client.getHabbo());
    if (!error.equals(FurnitureMovementError.NONE)) {
      messageHandler.client.sendResponse((MessageComposer)new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
      messageHandler.client.sendResponse((MessageComposer)new FloorItemUpdateComposer(item));
    } 
  }
  
  public FurnitureMovementError moveFurniTo(Room room, HabboItem item, RoomTile tile, int rotation, Habbo actor) {
    double height;
    RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
    boolean pluginHelper = false;
    if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
      FurnitureMovedEvent event = (FurnitureMovedEvent)Emulator.getPluginManager().fireEvent((Event)new FurnitureMovedEvent(item, actor, oldLocation, tile));
      if (event.isCancelled())
        return FurnitureMovementError.CANCEL_PLUGIN_MOVE; 
      pluginHelper = event.hasPluginHelper();
    } 
    HabboItem topItem = room.getTopItemAt(tile.x, tile.y);
    boolean magicTile = item instanceof com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
    boolean canHaveUser = false;
    if (item.getBaseItem().allowSit() || item.getBaseItem().allowWalk() || item.getBaseItem().allowLay())
      canHaveUser = true; 
    Optional<HabboItem> stackHelper = room.getItemsAt(tile).stream().filter(i -> i instanceof com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper).findAny();
    THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
    if (!stackHelper.isPresent() && !pluginHelper) {
      if (topItem != item)
        for (TObjectHashIterator<RoomTile> tObjectHashIterator2 = occupiedTiles.iterator(); tObjectHashIterator2.hasNext(); ) {
          RoomTile t = tObjectHashIterator2.next();
          HabboItem tileTopItem = room.getTopItemAt(t.x, t.y);
          if (!magicTile && ((tileTopItem != null) ? ((tileTopItem != item) ? (t.state.equals(RoomTileState.INVALID) || !t.getAllowStack() || !tileTopItem.getBaseItem().allowStack()) : calculateTileState(room, t, item).equals(RoomTileState.INVALID)) : calculateTileState(room, t, item).equals(RoomTileState.INVALID)))
            return FurnitureMovementError.CANT_STACK; 
          if (!magicTile && !canHaveUser && room.hasHabbosAt(t.x, t.y))
            return FurnitureMovementError.TILE_HAS_HABBOS; 
          if (!magicTile && room.hasBotsAt(t.x, t.y))
            return FurnitureMovementError.TILE_HAS_BOTS; 
          if (!magicTile && room.hasPetsAt(t.x, t.y))
            return FurnitureMovementError.TILE_HAS_PETS; 
        }  
      List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new ArrayList<>();
      for (TObjectHashIterator<RoomTile> tObjectHashIterator1 = occupiedTiles.iterator(); tObjectHashIterator1.hasNext(); ) {
        RoomTile t = tObjectHashIterator1.next();
        tileFurniList.add(Pair.create(t, room.getItemsAt(t)));
      } 
      if (!magicTile && !item.canStackAt(room, tileFurniList))
        return FurnitureMovementError.CANT_STACK; 
    } 
    THashSet<RoomTile> oldOccupiedTiles = room.getLayout().getTilesAt(room.getLayout().getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
    int oldRotation = item.getRotation();
    item.setRotation(rotation);
    if (oldRotation != rotation && 
      Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
      FurnitureRotatedEvent furnitureRotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
      Emulator.getPluginManager().fireEvent((Event)furnitureRotatedEvent);
      if (furnitureRotatedEvent.isCancelled()) {
        item.setRotation(oldRotation);
        return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
      } 
    } 
    if (stackHelper.isPresent()) {
      height = ((HabboItem)stackHelper.get()).getExtradata().isEmpty() ? Double.parseDouble("0.0") : (Double.parseDouble(((HabboItem)stackHelper.get()).getExtradata()) / 100.0D);
    } else if (item.equals(topItem) && tile.x == item.getX() && tile.y == item.getY()) {
      height = item.getZ();
    } else {
      height = room.getStackHeight(tile.x, tile.y, false, item);
    } 
    if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
      FurnitureBuildheightEvent event = (FurnitureBuildheightEvent)Emulator.getPluginManager().fireEvent((Event)new FurnitureBuildheightEvent(item, actor, 0.0D, height));
      if (event.hasChangedHeight())
        height = event.getUpdatedHeight(); 
    } 
    if (height > 101.0D)
      return FurnitureMovementError.CANT_STACK; 
    item.setX(tile.x);
    item.setY(tile.y);
    item.setZ(height);
    if (magicTile) {
      item.setZ(tile.z);
      item.setExtradata("" + (item.getZ() * 100.0D));
    } 
    if (item.getZ() > 101.0D)
      item.setZ(101.0D);
    item.onMove(room, oldLocation, tile);
    item.needsUpdate(true);
    Emulator.getThreading().run((Runnable)item);
    room.sendComposer((new FloorItemUpdateComposer(item)).compose());
    occupiedTiles.removeAll((Collection)oldOccupiedTiles);
    occupiedTiles.addAll((Collection)oldOccupiedTiles);
    room.updateTiles(occupiedTiles);
    for (TObjectHashIterator<RoomTile> tObjectHashIterator = occupiedTiles.iterator(); tObjectHashIterator.hasNext(); ) {
      RoomTile t = tObjectHashIterator.next();
      room.updateHabbosAt(t.x, t.y, (THashSet)room
          
          .getHabbosAt(t.x, t.y)
          .stream()
          .filter(h -> !h.getRoomUnit().hasStatus(RoomUnitStatus.MOVE))
          .collect(Collectors.toCollection(THashSet::new)));
      room.updateBotsAt(t.x, t.y);
    } 
    return FurnitureMovementError.NONE;
  }
  
  private RoomTileState calculateTileState(Room room, RoomTile tile, HabboItem exclude) {
    if (tile == null || tile.state == RoomTileState.INVALID)
      return RoomTileState.INVALID; 
    RoomTileState result = RoomTileState.OPEN;
    HabboItem highestItem = null;
    HabboItem lowestChair = room.getLowestChair(tile);
    THashSet<HabboItem> items = room.getItemsAt(tile);
    if (items == null)
      return RoomTileState.INVALID; 
    for (TObjectHashIterator<HabboItem> tObjectHashIterator = items.iterator(); tObjectHashIterator.hasNext(); ) {
      HabboItem item = tObjectHashIterator.next();
      if (exclude != null && item == exclude)
        continue; 
      if (item.getBaseItem().allowLay())
        return RoomTileState.LAY; 
      if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem) > item.getZ() + Item.getCurrentHeight(item))
        continue; 
      highestItem = item;
      if (result == RoomTileState.OPEN)
        result = checkStateForItem(room, item, tile); 
    } 
    if (lowestChair != null)
      return RoomTileState.SIT; 
    return result;
  }
  
  private RoomTileState checkStateForItem(Room room, HabboItem item, RoomTile tile) {
    RoomTileState result = RoomTileState.BLOCKED;
    if (item.isWalkable())
      result = RoomTileState.OPEN; 
    if (item.getBaseItem().allowSit())
      result = RoomTileState.SIT; 
    if (item.getBaseItem().allowLay())
      result = RoomTileState.LAY; 
    RoomTileState overriddenState = item.getOverrideTileState(tile, room);
    if (overriddenState != null)
      result = overriddenState; 
    return result;
  }
}
