package com.example.lifestealsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LifestealSMP extends JavaPlugin implements Listener {
    private NamespacedKey weaponKey;
    private NamespacedKey speedKey;
    private static final String LIFESTEALER = "lifestealer";
    private static final String SWORD_AXE = "sword_axe";
    private static final String ARMOR_BREAKER = "armor_breaker";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        weaponKey = new NamespacedKey(this, "unique_weapon");
        speedKey = new NamespacedKey(this, "sword_axe_attack_speed");
        getServer().getPluginManager().registerEvents(this, this);
        registerRecipes();
        getLogger().info("LifestealSMP enabled for Paper 26.2.");
    }

    private int clampHearts(int hearts) {
        return Math.max(getConfig().getInt("minimum-hearts", 0),
                Math.min(getConfig().getInt("maximum-hearts", 20), hearts));
    }

    private int hearts(Player player) {
        return (int) Math.round(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue() / 2.0);
    }

    private void setHearts(Player player, int hearts) {
        int clamped = clampHearts(hearts);
        // Minecraft entities need positive health even when their max-heart count is zero.
        double maxHealth = Math.max(1.0, clamped * 2.0);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        if (player.getHealth() > maxHealth) player.setHealth(maxHealth);
        getConfig().set("players." + player.getUniqueId() + ".hearts", clamped);
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int stored = getConfig().getInt("players." + player.getUniqueId() + ".hearts",
                getConfig().getInt("starting-hearts", 10));
        setHearts(player, stored);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;

        boolean usedLifestealer = isWeapon(killer.getInventory().getItemInMainHand(), LIFESTEALER);
        int stolen = usedLifestealer ? 3 : 1;
        setHearts(victim, hearts(victim) - stolen);
        setHearts(killer, hearts(killer) + stolen);

        victim.sendMessage(Component.text("You lost " + stolen + " heart" + (stolen == 1 ? "" : "s") +
                " to " + killer.getName() + ".", NamedTextColor.RED));
        killer.sendMessage(Component.text("You stole " + stolen + " heart" + (stolen == 1 ? "" : "s") +
                " from " + victim.getName() + "!", NamedTextColor.GREEN));

        if (hearts(victim) <= 0 && getConfig().getBoolean("ban-at-zero-hearts", true)) {
            Bukkit.getBanList(BanList.Type.PROFILE).addBan(victim.getPlayerProfile(),
                    "Eliminated: you ran out of hearts.", null, "LifestealSMP");
            Bukkit.getScheduler().runTask(this, () ->
                    victim.kick(Component.text("You ran out of hearts and have been eliminated.", NamedTextColor.RED)));
        }
    }

    private ItemStack makeWeapon(String id) {
        ItemStack item = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = item.getItemMeta();

        switch (id) {
            case LIFESTEALER -> {
                meta.displayName(Component.text("Lifestealer Axe", NamedTextColor.DARK_RED));
                meta.lore(List.of(
                        Component.text("Steals 3 hearts per player kill", NamedTextColor.GRAY),
                        Component.text("Unique server relic", NamedTextColor.DARK_PURPLE)));
                meta.setUnbreakable(true);
            }
            case SWORD_AXE -> {
                meta.displayName(Component.text("Sword Axe", NamedTextColor.AQUA));
                meta.lore(List.of(
                        Component.text("Sword-speed axe", NamedTextColor.GRAY),
                        Component.text("Sword enchantments can be transferred in an anvil", NamedTextColor.GRAY)));
                // Normal axe attack speed is -3.1; adding +0.7 makes it match the sword's -2.4 modifier.
                meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                        new AttributeModifier(speedKey, 0.7, AttributeModifier.Operation.ADD_NUMBER));
            }
            case ARMOR_BREAKER -> {
                meta.displayName(Component.text("Armor Breaker Axe", NamedTextColor.GOLD));
                meta.lore(List.of(Component.text("Deals extra durability damage to worn armor", NamedTextColor.GRAY)));
            }
            default -> throw new IllegalArgumentException("Unknown weapon id: " + id);
        }

        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private String weaponId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
    }

    private boolean isWeapon(ItemStack item, String id) {
        return id.equals(weaponId(item));
    }

    private boolean isSpecial(ItemStack item) {
        String id = weaponId(item);
        return LIFESTEALER.equals(id) || SWORD_AXE.equals(id) || ARMOR_BREAKER.equals(id);
    }

    private boolean containsSpecial(ItemStack item) {
        if (isSpecial(item)) return true;
        if (item == null || !item.hasItemMeta() || !(item.getItemMeta() instanceof BlockStateMeta meta)) return false;
        if (!(meta.getBlockState() instanceof org.bukkit.block.ShulkerBox shulker)) return false;
        for (ItemStack content : shulker.getInventory().getContents()) {
            if (isSpecial(content)) return true;
        }
        return false;
    }

    private void registerRecipes() {
        // 4 diamond blocks, 4 enchanted golden apples, 1 netherite axe.
        ShapedRecipe lifestealer = new ShapedRecipe(new NamespacedKey(this, LIFESTEALER), makeWeapon(LIFESTEALER));
        lifestealer.shape("DGD", "GAG", "DGD");
        lifestealer.setIngredient('D', Material.DIAMOND_BLOCK);
        lifestealer.setIngredient('G', Material.ENCHANTED_GOLDEN_APPLE);
        lifestealer.setIngredient('A', Material.NETHERITE_AXE);
        Bukkit.addRecipe(lifestealer);

        // 6 diamond blocks, 1 netherite sword, 1 netherite axe, 1 netherite upgrade template.
        ShapedRecipe swordAxe = new ShapedRecipe(new NamespacedKey(this, SWORD_AXE), makeWeapon(SWORD_AXE));
        swordAxe.shape("DDD", "DSU", "DAD");
        swordAxe.setIngredient('D', Material.DIAMOND_BLOCK);
        swordAxe.setIngredient('S', Material.NETHERITE_SWORD);
        swordAxe.setIngredient('U', Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        swordAxe.setIngredient('A', Material.NETHERITE_AXE);
        Bukkit.addRecipe(swordAxe);

        // 4 diamond blocks, 2 netherite ingots, 2 enchanted golden apples, 1 netherite axe.
        ShapedRecipe armorBreaker = new ShapedRecipe(new NamespacedKey(this, ARMOR_BREAKER), makeWeapon(ARMOR_BREAKER));
        armorBreaker.shape("DID", "GAG", "DID");
        armorBreaker.setIngredient('D', Material.DIAMOND_BLOCK);
        armorBreaker.setIngredient('I', Material.NETHERITE_INGOT);
        armorBreaker.setIngredient('G', Material.ENCHANTED_GOLDEN_APPLE);
        armorBreaker.setIngredient('A', Material.NETHERITE_AXE);
        Bukkit.addRecipe(armorBreaker);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (!isSpecial(result)) return;

        String id = weaponId(result);
        String path = "crafted." + id;
        if (getConfig().getBoolean(path, false)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(Component.text("This legendary weapon has already been crafted.", NamedTextColor.RED));
            return;
        }
        // Shift-click and collect-to-cursor can exploit recipe output handling; require a normal single click.
        if (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(Component.text("Take this unique weapon with a normal click.", NamedTextColor.YELLOW));
            return;
        }
        getConfig().set(path, true);
        saveConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorBreakerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        if (!isWeapon(attacker.getInventory().getItemInMainHand(), ARMOR_BREAKER)) return;

        // Vanilla handles its normal armor wear. Add two extra durability points per worn armor piece
        // to make an ordinary hit approximately three times as costly to armor durability.
        for (ItemStack armor : victim.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir() || !(armor.getItemMeta() instanceof Damageable damageable)) continue;
            if (armor.getType().getMaxDurability() <= 0) continue;
            damageable.setDamage(Math.min(armor.getType().getMaxDurability(), damageable.getDamage() + 2));
            armor.setItemMeta((ItemMeta) damageable);
        }
    }

    @EventHandler
    public void onWeaponDamage(PlayerItemDamageEvent event) {
        // Lifestealer Axe is indestructible; the other two weapons keep normal durability.
        if (isWeapon(event.getItem(), LIFESTEALER)) event.setCancelled(true);
    }

    @EventHandler
    public void onContainerClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        boolean containerView = top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER;
        if (!containerView) return;

        if (containsSpecial(event.getCurrentItem()) || containsSpecial(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && containsSpecial(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick().isKeyboardClick()) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && containsSpecial(event.getWhoClicked().getInventory().getItem(hotbar))) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onContainerDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        boolean containerView = top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER;
        if (!containerView || !containsSpecial(event.getOldCursor())) return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (containsSpecial(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isSpecial(event.getItem().getItemStack()) && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDespawn(ItemDespawnEvent event) {
        if (isSpecial(event.getEntity().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item) || !isSpecial(item.getItemStack())) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.FIRE ||
                cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemBurn(EntityCombustEvent event) {
        if (event.getEntity() instanceof Item item && isSpecial(item.getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onItemMerge(ItemMergeEvent event) {
        if (isSpecial(event.getEntity().getItemStack()) || isSpecial(event.getTarget().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (!isWeapon(first, SWORD_AXE) || second == null || second.getType().isAir()) return;

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) result = first.clone();
        ItemMeta meta = result.getItemMeta();
        for (Map.Entry<Enchantment, Integer> enchantment : second.getEnchantments().entrySet()) {
            String key = enchantment.getKey().getKey().getKey();
            if (List.of("sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
                    "looting", "sweeping_edge", "unbreaking", "mending", "vanishing_curse",
                    "binding_curse").contains(key)) {
                meta.addEnchant(enchantment.getKey(), enchantment.getValue(), true);
            }
        }
        result.setItemMeta(meta);
        event.setResult(result);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("hearts")) {
            if (sender instanceof Player player) sender.sendMessage(Component.text("You have " + hearts(player) + " hearts.", NamedTextColor.LIGHT_PURPLE));
            else sender.sendMessage("This command is for players.");
            return true;
        }
        if (!sender.hasPermission("lifestealsmp.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/lifesteal give <player> <lifestealer|swordaxe|armorbreaker>");
            sender.sendMessage("/lifesteal sethearts <player> <amount>");
            sender.sendMessage("/lifesteal recipe");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (args.length < 3) return false;
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage("Player must be online."); return true; }
                String id = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "lifestealer" -> LIFESTEALER;
                    case "swordaxe" -> SWORD_AXE;
                    case "armorbreaker" -> ARMOR_BREAKER;
                    default -> null;
                };
                if (id == null) { sender.sendMessage("Unknown weapon."); return true; }
                Map<Integer, ItemStack> overflow = target.getInventory().addItem(makeWeapon(id));
                overflow.values().forEach(drop -> target.getWorld().dropItemNaturally(target.getLocation(), drop));
                sender.sendMessage("Granted " + id + " to " + target.getName() + ". This does not reset its recipe lock.");
                return true;
            }
            case "sethearts" -> {
                if (args.length < 3) return false;
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage("Player must be online."); return true; }
                try {
                    setHearts(target, Integer.parseInt(args[2]));
                    sender.sendMessage("Set " + target.getName() + " to " + hearts(target) + " hearts.");
                } catch (NumberFormatException exception) {
                    sender.sendMessage("Amount must be a number.");
                }
                return true;
            }
            case "recipe" -> {
                sender.sendMessage("Lifestealer: diamond blocks in corners, enchanted golden apples on edges, netherite axe center.");
                sender.sendMessage("Sword Axe: 6 diamond blocks, netherite sword, netherite axe, netherite upgrade template.");
                sender.sendMessage("Armor Breaker: 4 diamond blocks corners, 2 netherite ingots, 2 enchanted golden apples, netherite axe center.");
                return true;
            }
            default -> {
                sender.sendMessage("Unknown subcommand.");
                return true;
            }
        }
    }
}
