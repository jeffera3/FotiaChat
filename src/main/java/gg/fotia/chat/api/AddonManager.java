package gg.fotia.chat.api;

import gg.fotia.chat.FotiaChat;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Addon管理器
 * 负责加载、卸载和管理所有Addon
 */
public class AddonManager {

    private final FotiaChat plugin;
    private final File addonsFolder;
    private final Map<String, FotiaChatAddon> addons = new LinkedHashMap<>();
    private final Map<String, AddonClassLoader> loaders = new HashMap<>();

    public AddonManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.addonsFolder = new File(plugin.getDataFolder(), "addons");
        if (!addonsFolder.exists()) {
            addonsFolder.mkdirs();
        }
    }

    /**
     * 加载所有Addon
     */
    public void loadAddons() {
        plugin.getLogger().info("正在加载Addon...");

        File[] files = addonsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("没有找到任何Addon");
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        // 第一步：读取所有Addon信息
        Map<String, File> addonFiles = new HashMap<>();
        Map<String, AddonInfo> addonInfos = new HashMap<>();

        for (File file : files) {
            try {
                AddonInfo info = loadAddonInfo(file);
                if (info != null) {
                    if (addonFiles.containsKey(info.getName())) {
                        plugin.getLogger().warning("检测到重名Addon " + info.getName()
                                + "，保留 " + addonFiles.get(info.getName()).getName()
                                + "，跳过 " + file.getName());
                        continue;
                    }
                    addonFiles.put(info.getName(), file);
                    addonInfos.put(info.getName(), info);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("无法读取Addon信息: " + file.getName() + " - " + throwable.getMessage());
            }
        }

        // 第二步：按依赖顺序排序
        List<String> loadOrder = sortByDependencies(addonInfos);

        // 第三步：按顺序加载Addon
        for (String name : loadOrder) {
            File file = addonFiles.get(name);
            AddonInfo info = addonInfos.get(name);

            // 检查依赖
            if (!checkDependencies(info)) {
                plugin.getLogger().warning("Addon " + name + " 缺少依赖，跳过加载");
                continue;
            }

            try {
                loadAddon(file, info);
            } catch (Throwable throwable) {
                plugin.getLogger().severe("加载Addon失败: " + name + " - " + throwable.getMessage());
            }
        }

        plugin.getLogger().info("已加载 " + addons.size() + " 个Addon");
    }

    /**
     * 从jar文件读取Addon信息
     */
    private AddonInfo loadAddonInfo(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("addon.yml");
            if (entry == null) {
                plugin.getLogger().warning(file.getName() + " 不是有效的Addon（缺少addon.yml）");
                return null;
            }

            try (InputStream is = jar.getInputStream(entry)) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(is, StandardCharsets.UTF_8));

                String name = config.getString("name");
                String version = config.getString("version", "1.0.0");
                String main = config.getString("main");
                String author = config.getString("author", "Unknown");
                String description = config.getString("description", "");
                List<String> depend = config.getStringList("depend");
                List<String> softDepend = config.getStringList("softdepend");

                if (name == null || name.isEmpty()) {
                    throw new Exception("addon.yml 缺少 name 字段");
                }
                if (main == null || main.isEmpty()) {
                    throw new Exception("addon.yml 缺少 main 字段");
                }

                return new AddonInfo(name, version, main, author, description, depend, softDepend);
            }
        }
    }

    /**
     * 按依赖关系排序
     */
    private List<String> sortByDependencies(Map<String, AddonInfo> addonInfos) {
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String name : addonInfos.keySet()) {
            visitDependency(name, addonInfos, sorted, visited, new HashSet<>());
        }

        return sorted;
    }

    private void visitDependency(String name, Map<String, AddonInfo> addonInfos,
                                  List<String> sorted, Set<String> visited, Set<String> visiting) {
        if (visited.contains(name)) {
            return;
        }
        if (visiting.contains(name)) {
            plugin.getLogger().warning("检测到循环依赖: " + name);
            return;
        }

        visiting.add(name);

        AddonInfo info = addonInfos.get(name);
        if (info != null) {
            for (String dep : info.getDepend()) {
                if (addonInfos.containsKey(dep)) {
                    visitDependency(dep, addonInfos, sorted, visited, visiting);
                }
            }
            for (String dep : info.getSoftDepend()) {
                if (addonInfos.containsKey(dep)) {
                    visitDependency(dep, addonInfos, sorted, visited, visiting);
                }
            }
        }

        visiting.remove(name);
        visited.add(name);
        sorted.add(name);
    }

    /**
     * 检查依赖是否满足
     */
    private boolean checkDependencies(AddonInfo info) {
        for (String dep : info.getDepend()) {
            if (!addons.containsKey(dep)) {
                plugin.getLogger().warning("Addon " + info.getName() + " 需要依赖 " + dep + " 但未找到");
                return false;
            }
        }
        return true;
    }

    /**
     * 加载单个Addon
     */
    private void loadAddon(File file, AddonInfo info) throws Exception {
        plugin.getLogger().info("正在加载Addon: " + info.getName() + " v" + info.getVersion());

        AddonClassLoader loader = new AddonClassLoader(file, info, getClass().getClassLoader());
        try {
            FotiaChatAddon addon = loader.loadAddon();
            File dataFolder = new File(addonsFolder, info.getName());
            addon.init(plugin, info, dataFolder, loader);
            addon.onEnable();
            addon.setEnabled(true);
            loaders.put(info.getName(), loader);
            addons.put(info.getName(), addon);
            plugin.getLogger().info("Addon " + info.getName() + " 已启用");
        } catch (Throwable throwable) {
            try {
                loader.close();
            } catch (Exception closeException) {
                plugin.getLogger().warning("关闭失败Addon类加载器时出错: " + info.getName());
            }
            loaders.remove(info.getName());
            addons.remove(info.getName());
            if (throwable instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(throwable);
        }
    }

    /**
     * 卸载所有Addon
     */
    public void unloadAddons() {
        // 按相反顺序卸载
        List<String> names = new ArrayList<>(addons.keySet());
        Collections.reverse(names);

        for (String name : names) {
            unloadAddon(name);
        }
    }

    /**
     * 卸载单个Addon
     */
    public void unloadAddon(String name) {
        FotiaChatAddon addon = addons.remove(name);
        if (addon != null) {
            try {
                addon.onDisable();
                addon.setEnabled(false);
                plugin.getLogger().info("Addon " + name + " 已禁用");
            } catch (Throwable throwable) {
                plugin.getLogger().severe("禁用Addon失败: " + name + " - " + throwable.getMessage());
            }
        }

        // 即使初始化失败、addons 中没有实例，也必须关闭已创建的类加载器
        AddonClassLoader loader = loaders.remove(name);
        if (loader != null) {
            try {
                loader.close();
            } catch (Exception e) {
                plugin.getLogger().warning("关闭Addon类加载器失败: " + name);
            }
        }
    }

    /**
     * 重载所有Addon
     */
    public void reloadAddons() {
        for (FotiaChatAddon addon : addons.values()) {
            try {
                addon.onReload();
                plugin.getLogger().info("Addon " + addon.getName() + " 已重载");
            } catch (Throwable throwable) {
                plugin.getLogger().severe("重载Addon失败: " + addon.getName() + " - " + throwable.getMessage());
            }
        }
    }

    /**
     * 获取Addon
     */
    public FotiaChatAddon getAddon(String name) {
        return addons.get(name);
    }

    /**
     * 获取所有Addon
     */
    public Collection<FotiaChatAddon> getAddons() {
        return Collections.unmodifiableCollection(addons.values());
    }

    /**
     * 检查Addon是否已加载
     */
    public boolean isLoaded(String name) {
        return addons.containsKey(name);
    }

    /**
     * 获取Addon数量
     */
    public int getAddonCount() {
        return addons.size();
    }

    /**
     * 获取Addon文件夹
     */
    public File getAddonsFolder() {
        return addonsFolder;
    }
}
