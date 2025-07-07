package dev.tizu.headmate.headmate;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import dev.tizu.headmate.ThisPlugin;
import dev.tizu.headmate.util.Transformers;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class HeadmateStore {
	private HeadmateStore() {
	}

	public static boolean has(Block block) {
		var entities = block.getWorld().getNearbyEntities(block.getLocation(), 0.1, 0.1, 0.1);
		return entities.stream().anyMatch(e -> e instanceof ItemDisplay de && has(de));
	}

	public static boolean has(ItemDisplay head) {
		return head.getPersistentDataContainer().has(getKey(), new HeadmateInstanceDataType());
	}

	public static HeadmateInstance get(ItemDisplay head) {
		return head.getPersistentDataContainer().get(getKey(), new HeadmateInstanceDataType());
	}

	public static void set(ItemDisplay head, HeadmateInstance inst) {
		head.setTransformation(inst.getTransformation());
		head.getPersistentDataContainer().set(getKey(), new HeadmateInstanceDataType(), inst);
	}

	public static ItemDisplay create(Block block) {
		if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD)
			throw new IllegalArgumentException("Invalid block type, got " + block.getType());

		var skull = (Skull) block.getState();
		var blockdata = skull.getBlockData();
		var profile = skull.getPlayerProfile();
		block.setType(Material.STRUCTURE_VOID);
		return HeadmateStore.add(block, profile != null ? ResolvableProfile.resolvableProfile(profile) : null,
				Transformers.getPos(blockdata), Transformers.getRot(blockdata));
	}

	public static ItemDisplay add(Block block, ResolvableProfile profile, Vector3f position, int rotation) {
		var world = block.getWorld();

		// transfer the player head to the item display
		var item = new ItemStack(Material.PLAYER_HEAD);
		if (profile != null)
			item.setData(DataComponentTypes.PROFILE, profile);

		var inst = new HeadmateInstance(position.x, position.y, position.z, 0.5f, rotation, 0);
		var entity = world.spawnEntity(block.getLocation(), EntityType.ITEM_DISPLAY, SpawnReason.CUSTOM, e -> {
			var de = (ItemDisplay) e;
			de.setItemStack(item);
			de.addScoreboardTag("headmate");
			HeadmateStore.set(de, inst);
		});
		return (ItemDisplay) entity;
	}

	public static ItemDisplay add(Block block, ResolvableProfile profile, float yaw) {
		return add(block, profile, new Vector3f(0f, -0.25f, 0f), Transformers.getRotIndex(-yaw));
	}

	public static void remove(Block block, UUID uuid) {
		var entity = block.getWorld().getEntity(uuid);
		if (entity != null) {
			entity.remove();
			var de = (ItemDisplay) entity;
			block.getWorld().spawnEntity(de.getLocation(), EntityType.ITEM, SpawnReason.NATURAL, e -> {
				var item = (Item) e;
				item.setItemStack(de.getItemStack());
			});
		}

		if (getCount(block) == 0 && (block.getType() == Material.STRUCTURE_VOID
				|| block.getType() == Material.BARRIER))
			block.setType(Material.AIR);
	}

	public static void removeAll(Block block) {
		for (var head : getHeads(block))
			remove(block, head.getUniqueId());
	}

	public static ItemDisplay[] getHeads(Block block) {
		var entities = block.getWorld().getNearbyEntities(block.getLocation(), 0.1, 0.1, 0.1);
		var heads = new ArrayList<ItemDisplay>();
		for (var e : entities) {
			if (!(e instanceof ItemDisplay ed))
				continue;
			if (HeadmateStore.has(ed))
				heads.add(ed);
		}
		return heads.toArray(new ItemDisplay[0]);
	}

	public static int getCount(Block block) {
		return getHeads(block).length;
	}

	public static void changeHitbox(Player player, Block block) {
		if (!HeadmateStore.has(block))
			return;
		// change hitbox. block -> drop & void, void -> barrier, barrier -> void
		switch (block.getType()) {
			case STRUCTURE_VOID:
				block.setType(Material.BARRIER);
				player.sendActionBar(Component.text("-> Solid block", NamedTextColor.RED));
				break;
			case BARRIER:
				block.setType(Material.STRUCTURE_VOID);
				player.sendActionBar(Component.text("-> Pass-through", NamedTextColor.RED));
				break;
			default:
				block.breakNaturally();
				block.setType(Material.STRUCTURE_VOID);
				player.sendActionBar(Component.text("-> Pass-through (dropped)",
						NamedTextColor.RED));
				break;
		}
	}

	private static NamespacedKey getKey() {
		return new NamespacedKey(ThisPlugin.i(), "head");
	}
}
