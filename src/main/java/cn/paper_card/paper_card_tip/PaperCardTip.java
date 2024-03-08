package cn.paper_card.paper_card_tip;

import cn.paper_card.database.api.DatabaseApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;

public final class PaperCardTip extends JavaPlugin {

    private final @NotNull TaskScheduler taskScheduler;

    private TipApiImpl tipApi = null;

    public PaperCardTip() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }

    @Nullable TipApiImpl getTipApi() {
        return this.tipApi;
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.LIGHT_PURPLE));
        text.append(Component.text(this.getName()).color(NamedTextColor.AQUA));
        text.append(Component.text("]").color(NamedTextColor.LIGHT_PURPLE));
    }

    int getIndex() {
        return this.getConfig().getInt("index", 0);
    }

    void setIndex(int i) {
        this.getConfig().set("index", i);
    }

    @Override
    public void onLoad() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到" + DatabaseApi.class.getSimpleName());

        this.tipApi = new TipApiImpl(api.getRemoteMySQL().getConnectionImportant());
        this.getServer().getServicesManager().register(PaperCardTipApi.class, this.tipApi, this, ServicePriority.Highest);
    }

    @Override
    public void onEnable() {
        new TipCommand(this);

        this.setIndex(this.getIndex());
        this.saveConfig();

        final TipApiImpl api = this.tipApi;
        if (api != null) {
            try {
                final int n = api.queryCount();
                this.getLogger().info("一共%d条Tip".formatted(n));
            } catch (SQLException e) {
                this.getSLF4JLogger().error("", e);
            }
        }

        this.taskScheduler.runTaskLaterAsynchronously(this.broadcastTask(), 20 * 60);
    }

    @NotNull Runnable broadcastTask() {
        return new Runnable() {

            private void next() {
                taskScheduler.runTaskLaterAsynchronously(this, 20 * 60 * 10);
            }

            @Override
            public void run() {

                final TipApiImpl api = tipApi;
                if (api == null) {
                    next();
                    return;
                }

                final int count;
                try {
                    count = api.queryCount();
                } catch (SQLException e) {
                    getSLF4JLogger().error("", e);
                    next();
                    return;
                }

                if (count <= 0) {
                    next();
                    return;
                }

                int i = getIndex();
                i %= count;

                final List<PaperCardTipApi.Tip> tips;
                try {
                    tips = api.queryByPage(1, i);
                } catch (SQLException e) {
                    getSLF4JLogger().error("", e);
                    next();
                    return;
                }

                final int size = tips.size();
                if (size == 1) {
                    final PaperCardTipApi.Tip tip = tips.get(0);
                    getServer().broadcast(Component.text()
                            .append(Component.text("[你知道吗？#%d]".formatted(tip.id())).color(NamedTextColor.GREEN))
                            .appendSpace()
                            .append(Component.text(tip.content()).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                            .append(Component.text("  --"))
                            .append(Component.text(tip.category()).color(NamedTextColor.GOLD).decorate(TextDecoration.ITALIC))
                            .build()
                    );
                }

                i += 1;
                i %= count;
                setIndex(i);

                next();
            }
        };
    }

    @Override
    public void onDisable() {
        this.saveConfig();

        this.taskScheduler.cancelTasks(this);

        final TipApiImpl api = this.tipApi;
        this.tipApi = null;
        if (api != null) {
            try {
                api.destroy();
            } catch (SQLException e) {
                this.getSLF4JLogger().error("destroy", e);
            }
        }
    }


    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    void appendTipInfo(@NotNull TextComponent.Builder builder, @NotNull PaperCardTipApi.Tip tip) {
        builder.append(Component.text("ID: "));
        builder.append(Component.text(tip.id()));
        builder.appendNewline();

        builder.append(Component.text("内容: "));
        builder.append(Component.text(tip.content())
                .color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.copyToClipboard(tip.content()))
                .hoverEvent(HoverEvent.showText(Component.text("点击复制")))
        );
        builder.appendNewline();

        builder.append(Component.text("分类: "));
        builder.append(Component.text(tip.category())
                .color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.copyToClipboard(tip.category()))
                .hoverEvent(HoverEvent.showText(Component.text("点击复制")))
        );
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        final TextComponent.Builder text = Component.text();
        this.appendPrefix(text);
        text.appendSpace();
        text.append(Component.text(error).color(NamedTextColor.RED));
        sender.sendMessage(text.build());
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();
        this.appendPrefix(text);
        text.appendSpace();
        text.append(Component.text("==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        final TextComponent.Builder text = Component.text();
        this.appendPrefix(text);
        text.appendSpace();
        text.append(Component.text(info).color(NamedTextColor.GREEN));
        sender.sendMessage(text.build());
    }


    void sendWarning(@NotNull CommandSender sender, @NotNull String warning) {
        final TextComponent.Builder text = Component.text();
        this.appendPrefix(text);
        text.appendSpace();
        text.append(Component.text(warning).color(NamedTextColor.YELLOW));
        sender.sendMessage(text.build());
    }
}
