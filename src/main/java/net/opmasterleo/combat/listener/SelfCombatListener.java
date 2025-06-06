package net.opmasterleo.combat.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import net.opmasterleo.combat.Combat;

public class SelfCombatListener implements Listener {

    @EventHandler
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getDamager() instanceof Player damager && damager.getUniqueId().equals(player.getUniqueId())) {
            if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                Combat.getInstance().setCombat(player, player);
            }
            return;
        }

        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            if (shooter.getUniqueId().equals(player.getUniqueId())) {
                if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                    Combat.getInstance().setCombat(player, player);
                }
                return;
            }
        }

        if (Combat.getInstance().getConfig().getBoolean("link-tnt", true)) {
            Entity damager = event.getDamager();
            String entityTypeName = damager.getType().name();

            if (entityTypeName.equals("PRIMED_TNT") || entityTypeName.equals("MINECART_TNT")) {
                Player placer = Combat.getInstance().getCrystalManager().getPlacer(damager);

                if (placer != null && placer.getUniqueId().equals(player.getUniqueId())) {
                    if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                        Combat.getInstance().setCombat(player, player);
                    }
                    return;
                }
            }
        }

        if (Combat.getInstance().getConfig().getBoolean("link-fishing-rod", true) && event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter && shooter.getUniqueId().equals(player.getUniqueId())) {
                if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                    Combat.getInstance().setCombat(player, player);
                }
            }
        }
    }
}
