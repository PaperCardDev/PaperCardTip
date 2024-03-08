package cn.paper_card.paper_card_tip;

import cn.paper_card.database.api.DatabaseApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class PaperCardTip extends JavaPlugin implements PaperCardTipApi {

    private DatabaseApi.MySqlConnection mySqlConnection;

    private Table table = null;
    private Connection connection = null;

    private final @NotNull TaskScheduler taskScheduler;

    private MyScheduledTask myScheduledTask = null;

    public PaperCardTip() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.LIGHT_PURPLE));
        text.append(Component.text(this.getName()).color(NamedTextColor.AQUA));
        text.append(Component.text("]").color(NamedTextColor.LIGHT_PURPLE));
    }

    private @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon && this.table != null) return this.table;

        if (this.table != null) this.table.close();
        this.table = new Table(newCon);
        this.connection = newCon;
        return this.table;
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

        this.mySqlConnection = api.getRemoteMySQL().getConnectionImportant();
    }

    @Override
    public void onEnable() {
        new TipCommand(this);

        this.setIndex(this.getIndex());
        this.saveConfig();

        try {
            final int n = this.queryCount();
            this.getLogger().info("一共%d条Tip".formatted(n));
        } catch (SQLException e) {
            this.getSLF4JLogger().error("", e);
        }

        if (this.myScheduledTask == null) {
            this.myScheduledTask = getTaskScheduler().runTaskTimerAsynchronously(() -> {
                final int count;
                try {
                    count = this.queryCount();
                } catch (SQLException e) {
                    this.getSLF4JLogger().error("", e);
                    return;
                }

                if (count <= 0) return;

                int i = this.getIndex();
                i %= count;

                final List<Tip> tips;
                try {
                    tips = this.queryByPage(1, i);
                } catch (Exception e) {
                    this.getSLF4JLogger().error("", e);
                    return;
                }

                final int size = tips.size();
                if (size == 1) {
                    final Tip tip = tips.get(0);
                    this.getServer().broadcast(Component.text()
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
                this.setIndex(i);
            }, 20 * 60, 20 * 60 * 20);
        }
    }

    @Override
    public void onDisable() {
        this.saveConfig();

        if (this.myScheduledTask != null) {
            this.myScheduledTask.cancel();
            this.myScheduledTask = null;
        }

        synchronized (this.mySqlConnection) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    this.getSLF4JLogger().error("", e);
                }
                this.table = null;
            }
        }
    }

    @Override
    public int addTip(@NotNull Tip tip) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int id = t.insertTip(tip);
                this.mySqlConnection.setLastUseTime();
                return id;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean deleteTip(int id) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int deleted = t.deleteTipById(id);
                this.mySqlConnection.setLastUseTime();

                if (deleted == 1) return true;
                if (deleted == 0) return false;
                throw new Exception("根据一个ID删除了%d条数据！".formatted(deleted));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean updateTipById(@NotNull Tip tip) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.updateTipById(tip);
                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return true;
                if (updated == 0) return false;

                throw new Exception("根据一个ID更新了%d条数据！".formatted(updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable Tip queryById(int id) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<Tip> tips = t.queryTipById(id);
                final int size = tips.size();

                if (size == 1) return tips.get(0);
                if (size == 0) return null;
                throw new Exception("根据一个ID查询到了%d条数据！".formatted(size));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<Tip> queryByPage(int limit, int offset) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<Tip> tips = t.queryByPage(limit, offset);
                this.mySqlConnection.setLastUseTime();
                return tips;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public int queryCount() throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int n = t.queryCount();
                this.mySqlConnection.setLastUseTime();
                return n;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public int queryPlayerIndex(@NotNull UUID uuid) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final Integer index = t.queryPlayerIndex(uuid);
                this.mySqlConnection.setLastUseTime();
                if (index == null) return 0;
                return index;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean setPlayerIndex(@NotNull UUID uuid, int index) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.updatePlayerIndex(uuid, index);
                this.mySqlConnection.setLastUseTime();
                if (updated == 1) return false;
                if (updated == 0) {
                    final int inserted = t.insertPlayerIndex(uuid, index);
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                    return true;
                }
                throw new Exception("更新了%d条数据！".formatted(updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
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

    void appendTipInfo(@NotNull TextComponent.Builder builder, @NotNull Tip tip) {
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
