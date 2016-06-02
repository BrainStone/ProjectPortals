package com.gmail.trentech.pjp.listeners;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import com.flowpowered.math.vector.Vector3d;
import com.gmail.trentech.pjp.Main;
import com.gmail.trentech.pjp.data.object.Portal;
import com.gmail.trentech.pjp.data.object.PortalBuilder;
import com.gmail.trentech.pjp.events.ConstructPortalEvent;
import com.gmail.trentech.pjp.events.TeleportEvent;
import com.gmail.trentech.pjp.events.TeleportEvent.Local;
import com.gmail.trentech.pjp.events.TeleportEvent.Server;
import com.gmail.trentech.pjp.utils.ConfigManager;

import flavor.pie.spongee.Spongee;
import ninja.leaping.configurate.ConfigurationNode;

public class PortalListener {

	public static ConcurrentHashMap<UUID, PortalBuilder> builders = new ConcurrentHashMap<>();

	@Listener
	public void onConstructPortalEvent(ConstructPortalEvent event, @First Player player) {
		List<Location<World>> locations = event.getLocations();
		
		for(Location<World> location : event.getLocations()) {
			if(Portal.get(location).isPresent()) {
	        	player.sendMessage(Text.of(TextColors.DARK_RED, "Portals cannot over lap other portals"));
	        	event.setCancelled(true);
	        	return;
			}
		}

        ConfigurationNode config = new ConfigManager().getConfig();
        
        int size = config.getNode("options", "portal", "size").getInt();
        if(locations.size() > size) {
        	player.sendMessage(Text.of(TextColors.DARK_RED, "Portals cannot be larger than ", size, " blocks"));
        	event.setCancelled(true);
        	return;
        }
        
        if(locations.size() < 5) {
        	player.sendMessage(Text.of(TextColors.DARK_RED, "Portal too small"));
        	event.setCancelled(true);        	
        	return;
        }
	}

	@Listener
	public void onChangeBlockEvent(ChangeBlockEvent.Place event, @First Player player) {
		if(!builders.containsKey(player.getUniqueId())) {
			for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
				Location<World> location = transaction.getFinal().getLocation().get();		

				if(!Portal.get(location).isPresent()) {
					continue;
				}

				event.setCancelled(true);
				break;
			}
			return;
		}
		PortalBuilder builder = builders.get(player.getUniqueId());
		
		for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
			if(transaction.getFinal().getState().getType().equals(BlockTypes.FIRE)) {
				event.setCancelled(true);
				break;
			}
			
			Location<World> location = transaction.getFinal().getLocation().get();
			
			if(builder.isFill()) {
				builder.addFill(location);
			}else{
				builder.addFrame(location);
			}
		}
	}
	
	@Listener
	public void onChangeBlockEvent(ChangeBlockEvent.Break event, @First Player player) {
		if(!builders.containsKey(player.getUniqueId())) {
			for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
				Location<World> location = transaction.getFinal().getLocation().get();		

				if(!Portal.get(location).isPresent()) {
					continue;
				}

				event.setCancelled(true);
				break;
			}
			return;
		}
		PortalBuilder builder = builders.get(player.getUniqueId());
		
		for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
			Location<World> location = transaction.getFinal().getLocation().get();
			if(builder.isFill()) {
				builder.removeFill(location);
			}else{
				builder.removeFrame(location);
			}
		}
	}

	@Listener
	public void onDisplaceEntityEventMoveOther(DisplaceEntityEvent.Move event) {
		Entity entity = event.getTargetEntity();
		
		if (entity instanceof Player || !(entity instanceof Living || entity instanceof Item)) {
			return;
		}

		Location<World> location = entity.getLocation();		

		Optional<Portal> optionalPortal = Portal.get(location);
		
		if(!optionalPortal.isPresent()) {
			return;
		}
		Portal portal = optionalPortal.get();
		
		if(entity instanceof Item) {
			if(!new ConfigManager().getConfig().getNode("options", "portal", "teleport_item").getBoolean()) {
				return;
			}
		}
		if(entity instanceof Living) {
			if(!new ConfigManager().getConfig().getNode("options", "portal", "teleport_mob").getBoolean()) {
				return;
			}
		}
		
		Optional<Location<World>> optionalSpawnLocation = portal.getDestination();
		
		if(!optionalSpawnLocation.isPresent()) {
			return;
		}
		Location<World> spawnLocation = optionalSpawnLocation.get();
		
		entity.setLocation(spawnLocation);
	}
	
	@Listener
	public void onDisplaceEntityEventMovePlayer(DisplaceEntityEvent.Move event) {
		Entity entity = event.getTargetEntity();
		
		if (!(entity instanceof Player)) {
			return;
		}
		Player player = (Player) entity;

		Location<World> location = player.getLocation();		

		Optional<Portal> optionalPortal = Portal.get(location);
		
		if(!optionalPortal.isPresent()) {
			return;
		}
		Portal portal = optionalPortal.get();
		
		if(new ConfigManager().getConfig().getNode("options", "advanced_permissions").getBoolean()) {
			if(!player.hasPermission("pjp.portal." + Portal.get(portal.getFill().get(0)))) {
				player.sendMessage(Text.of(TextColors.DARK_RED, "You do not have permission to use this portal"));
				return;
			}
		}else{
			if(!player.hasPermission("pjp.portal.interact")) {
				player.sendMessage(Text.of(TextColors.DARK_RED, "You do not have permission to use portals"));
				return;
			}
		}

		if(portal.isBungee()) {
			String source = "source";
			
			Server teleportEvent = new TeleportEvent.Server(player, source, portal.getServer(), portal.getPrice(), Cause.of(NamedCause.source(portal)));

			if(!Main.getGame().getEventManager().post(teleportEvent)) {
				Spongee.API.connectPlayer(player, teleportEvent.getDestination());
			}
		}else {
			Optional<Location<World>> optionalSpawnLocation = portal.getDestination();
			
			if(!optionalSpawnLocation.isPresent()) {
				player.sendMessage(Text.of(TextColors.DARK_RED, "Spawn location does not exist or world is not loaded"));
				return;
			}
			Location<World> spawnLocation = optionalSpawnLocation.get();

			Local teleportEvent = new TeleportEvent.Local(player, player.getLocation(), spawnLocation, portal.getPrice(), Cause.of(NamedCause.source(portal)));

			if(!Main.getGame().getEventManager().post(teleportEvent)) {
				spawnLocation = teleportEvent.getDestination();
				
				Vector3d rotation = portal.getRotation().toVector3d();

				player.setLocationAndRotation(spawnLocation, rotation);
			}
		}
	}
}
