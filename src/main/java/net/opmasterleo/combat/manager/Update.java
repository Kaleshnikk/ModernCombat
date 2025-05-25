package net.opmasterleo.combat.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class Update {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/OPmasterLEO/MasterCombatX/releases/latest";
    private static String latestVersion;
    private static String downloadUrl;
    private static boolean updateConfirmationRequired = true;
    private static long confirmationTimeout = 0;

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static void checkForUpdates(Plugin plugin) {
        // Only print once, not in both command and async
        if (!Boolean.getBoolean("combat.update.checking")) {
            Bukkit.getConsoleSender().sendMessage("Checking for updates…");
            System.setProperty("combat.update.checking", "true");
        }
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> performUpdateCheck(plugin));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performUpdateCheck(plugin));
        }
    }

    public static void notifyOnServerOnline(Plugin plugin) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                String pluginName = plugin.getDescription().getName();
                String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
                String normalizedLatestVersion = latestVersion != null ? normalizeVersion(latestVersion) : null;

                if (latestVersion == null) {
                    Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
                    return;
                }

                if (currentVersion.equalsIgnoreCase(normalizedLatestVersion)) {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " (v" + currentVersion + ").");
                } else if (currentVersion.compareTo(normalizedLatestVersion) < 0) {
                    Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» An update is required, installed version v" + currentVersion + ", but latest is v" + normalizedLatestVersion + ".");
                    Bukkit.getConsoleSender().sendMessage("§eUse /combat update to update to the latest version.");
                } else {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running a newer version of " + pluginName + " (v" + currentVersion + ") but the latest is (v" + normalizedLatestVersion + ").");
                }
            }, 20L * 3);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String pluginName = plugin.getDescription().getName();
                String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
                String normalizedLatestVersion = latestVersion != null ? normalizeVersion(latestVersion) : null;

                if (latestVersion == null) {
                    Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
                    return;
                }

                if (currentVersion.equalsIgnoreCase(normalizedLatestVersion)) {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " (v" + currentVersion + ").");
                } else if (currentVersion.compareTo(normalizedLatestVersion) < 0) {
                    Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» An update is required, installed version v" + currentVersion + ", but latest is v" + normalizedLatestVersion + ".");
                    Bukkit.getConsoleSender().sendMessage("§eUse /combat update to update to the latest version.");
                } else {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running a newer version of " + pluginName + " (v" + currentVersion + ") but the latest is (v" + normalizedLatestVersion + ").");
                }
            }, 20L * 3);
        }
    }

    public static void notifyOnPlayerJoin(Player player, Plugin plugin) {
        if (!player.isOp()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("update-notify-chat", false)) {
            return;
        }

        String pluginName = plugin.getDescription().getName();
        String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
        String normalizedLatestVersion = latestVersion != null ? normalizeVersion(latestVersion) : null;

        if (latestVersion == null) {
            player.sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
            return;
        }

        if (currentVersion.equalsIgnoreCase(normalizedLatestVersion)) {
            player.sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " (v" + currentVersion + ").");
        } else if (currentVersion.compareTo(normalizedLatestVersion) > 0) {
            player.sendMessage("§a[" + pluginName + "]» This server is running a newer version of " + pluginName + " (v" + currentVersion + ") but the latest is (v" + normalizedLatestVersion + ").");
        } else {
            player.sendMessage("§e[" + pluginName + "]» This server is running " + pluginName + " version v" + currentVersion +
                    " but the latest is (v" + normalizedLatestVersion + ").");
            player.sendMessage("§e[" + pluginName + "]» Run /combat update again if you confirm to update the plugin version.");
        }
    }

    public static void downloadAndReplaceJar(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        if (latestVersion == null || downloadUrl == null) {
            Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» No update information available. Please check for updates first.");
            return;
        }

        String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
        String normalizedLatestVersion = normalizeVersion(latestVersion);

        if (!isNewerVersion(currentVersion, normalizedLatestVersion)) {
            if (currentVersion.compareTo(normalizedLatestVersion) > 0) {
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running a newer version of " + pluginName + " (v" + currentVersion + ") but the latest is (v" + normalizedLatestVersion + ").");
            } else {
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running the latest version of " + pluginName + " (v" + currentVersion + ").");
            }
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (updateConfirmationRequired || currentTime > confirmationTimeout) {
            Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» Run /combat update again if you confirm to update the plugin version.");
            updateConfirmationRequired = false;
            confirmationTimeout = currentTime + 10000;
            return;
        }

        Bukkit.getConsoleSender().sendMessage("Downloading and applying the update ...");
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> performJarReplacement(plugin));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performJarReplacement(plugin));
        }
    }

    private static void performUpdateCheck(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(GITHUB_API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                latestVersion = parseVersion(response.toString());
                downloadUrl = parseDownloadUrl(response.toString());

                String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
                String normalizedLatestVersion = latestVersion != null ? normalizeVersion(latestVersion) : null;

                if (latestVersion != null && !currentVersion.equalsIgnoreCase(normalizedLatestVersion)) {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» Update found!");
                }
            } else {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to check for updates. HTTP Response Code: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» An error occurred while checking for updates: " + e.getMessage());
        } finally {
            // After check, reset the flag so future checks print again
            System.clearProperty("combat.update.checking");
        }
    }

    private static void performJarReplacement(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        try {
            // Place update in plugins/update/ (standard for Bukkit/Paper)
            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists() && !updateFolder.mkdirs()) {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to create update folder.");
                return;
            }

            String normalizedVersion = latestVersion.replace(pluginName + "-", "").replace(pluginName, "");
            File tempFile = new File(updateFolder, pluginName + "-" + normalizedVersion + ".jar");

            HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» Update complete. The new version has been placed in the update folder.");
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» Please restart your server to apply the update.");
            } else {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to download the latest jar. File does not exist or is empty.");
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» An error occurred while downloading the jar: " + e.getMessage());
        }
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, Update.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (IllegalStateException e) {
            Bukkit.getConsoleSender().sendMessage("§c[MasterCombatX] Error checking for Folia: " + e.getMessage());
            return false;
        }
    }

    private static String normalizeVersion(String version) {
        return version.replaceAll("[^0-9.]", "");
    }

    private static String parseVersion(String jsonResponse) {
        String tagPrefix = "\"tag_name\":\"";
        int startIndex = jsonResponse.indexOf(tagPrefix) + tagPrefix.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        return jsonResponse.substring(startIndex, endIndex);
    }

    private static String parseDownloadUrl(String jsonResponse) {
        String urlPrefix = "\"browser_download_url\":\"";
        int startIndex = jsonResponse.indexOf(urlPrefix) + urlPrefix.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        return startIndex > urlPrefix.length() - 1 ? jsonResponse.substring(startIndex, endIndex) : null;
    }

    private static boolean isNewerVersion(String currentVersion, String latestVersion) {
        currentVersion = normalizeVersion(currentVersion);
        latestVersion = normalizeVersion(latestVersion);

        String[] currentParts = currentVersion.split("\\.");
        String[] latestParts = latestVersion.split("\\.");

        for (int i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

            if (currentPart < latestPart) {
                return true;
            } else if (currentPart > latestPart) {
                return false;
            }
        }

        return false;
    }
}