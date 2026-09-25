package dev.stym.tickradar.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.engine.SampleSlots;
import dev.stym.tickradar.player.DisplayPrefs;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.player.Permissions;
import dev.stym.tickradar.player.PlayerState;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class PlayerSamplerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000002a");

    private final Settings settings = new ConfigFiles(Path.of("unused"), PlayerSamplerTest.class.getClassLoader()::getResourceAsStream)
            .builtInDefaults(1);
    private final Set<String> granted = new HashSet<>(Permissions.ALL);
    private final OnlinePlayers online = new OnlinePlayers();
    private final Player player = player();
    private final PlayerSampler sampler = new PlayerSampler(context(), player, DisplayPrefs.NONE, retired -> {
    });

    @Test
    void togglingTheAlertsPublishesTheRereadPermissions() {
        online.publish(sampledState());
        granted.removeAll(List.of(Permissions.ALERTS, Permissions.TELEPORT));
        sampler.toggleAlerts(false);
        PlayerState state = online.get(ID).orElseThrow();
        assertTrue(state.canDisplay());
        assertFalse(state.canReceiveAlerts());
        assertFalse(state.canTeleport());
        assertFalse(state.prefs().alertsOn());
        assertEquals("R7", state.anchorId());
        assertEquals("en", state.language());
    }

    @Test
    void togglingADisplayPublishesTheRereadPermissions() {
        online.publish(sampledState());
        granted.remove(Permissions.DISPLAY);
        sampler.toggle(DisplayKind.BOSSBAR, true);
        PlayerState state = online.get(ID).orElseThrow();
        assertFalse(state.canDisplay());
        assertTrue(state.canReceiveAlerts());
        assertTrue(state.prefs().isShown(DisplayKind.BOSSBAR, false));
    }

    @Test
    void aGrantedPermissionIsPublishedAtOnceToo() {
        granted.remove(Permissions.ALERTS);
        online.publish(new PlayerState(ID, player, "R7", "en", true, false, true, DisplayPrefs.NONE));
        granted.add(Permissions.ALERTS);
        sampler.toggleAlerts(true);
        assertTrue(online.get(ID).orElseThrow().canReceiveAlerts());
    }

    @Test
    void nothingIsPublishedBeforeTheFirstSample() {
        sampler.toggleAlerts(false);
        sampler.toggle(DisplayKind.TAB, true);
        assertTrue(online.get(ID).isEmpty());
    }

    private PlayerState sampledState() {
        return new PlayerState(ID, player, "R7", "en", true, true, true, DisplayPrefs.NONE);
    }

    private SamplingContext context() {
        return new SamplingContext(null, () -> settings, () -> false, null, online, SamplingCosts.create(), null, null,
                new Renderer(), new NamespacedKey("tickradar", "prefs"), new SampleSlots());
    }

    private Player player() {
        PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(), new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType()));
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> ID;
                    case "hasPermission" -> arguments[0] instanceof String permission && granted.contains(permission);
                    case "getPersistentDataContainer" -> data;
                    case "locale" -> Locale.ENGLISH;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }
}
