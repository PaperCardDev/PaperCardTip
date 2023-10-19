package cn.paper_card.paper_card_tip;

import cn.paper_card.database.DatabaseApi;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public final class PaperCardTip extends JavaPlugin implements PaperCardTipApi {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Table table = null;
    private Connection connection = null;

    private final @NotNull TextComponent prefix;

    private final @NotNull TaskScheduler taskScheduler;

    public PaperCardTip() {
        @NotNull DatabaseApi api = this.getDatabaseApi0();
        this.mySqlConnection = api.getRemoteMySqlDb().getConnectionImportant();

        this.prefix = Component.text()
                .append(Component.text("[").color(NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(this.getName()).color(NamedTextColor.AQUA))
                .append(Component.text("]").color(NamedTextColor.LIGHT_PURPLE))
                .build();

        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }

    private @NotNull DatabaseApi getDatabaseApi0() {
        final Plugin plugin = this.getServer().getPluginManager().getPlugin("Database");
        if (plugin instanceof final DatabaseApi api) {
            return api;
        }
        throw new NoSuchElementException("Database插件未安装！");
    }

    private @NotNull Table getTable() throws SQLException {
        final Connection rowConnection = this.mySqlConnection.getRowConnection();
        if (this.connection == null) {
            this.connection = rowConnection;
            this.table = new Table(rowConnection);
            return this.table;
        } else if (this.connection == rowConnection) {
            return this.table;
        } else {
            this.connection = rowConnection;
            if (this.table != null) this.table.close();
            this.table = new Table(rowConnection);
            return this.table;
        }
    }

    @Override
    public void onEnable() {
        new TipCommand(this);
    }

    @Override
    public void onDisable() {
        synchronized (this.mySqlConnection) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    this.getLogger().severe(e.toString());
                    e.printStackTrace();
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
                    this.mySqlConnection.checkClosedException(e);
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
                    this.mySqlConnection.checkClosedException(e);
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
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable Tip queryById(int id) throws Exception {
        try {
            final Table t = this.getTable();
            final List<Tip> tips = t.queryTipById(id);
            final int size = tips.size();

            if (size == 1) return tips.get(0);
            if (size == 0) return null;
            throw new Exception("根据一个ID查询到了%d条数据！".formatted(size));
        } catch (SQLException e) {
            try {
                this.mySqlConnection.checkClosedException(e);
            } catch (SQLException ignored) {
            }
            throw e;
        }
    }

    @Override
    public @Nullable Tip queryOneTip(int offset) throws Exception {
        return null;
    }

    @Override
    public int queryCount() throws Exception {
        return 0;
    }

    @Override
    public int queryPlayerIndex(@NotNull UUID uuid) throws Exception {
        return 0;
    }

    @Override
    public void setPlayerIndex(@NotNull UUID uuid, int index) throws Exception {

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
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(error).color(NamedTextColor.RED))
                .build()
        );
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(info).color(NamedTextColor.GREEN))
                .build()
        );
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull TextComponent info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(info)
                .build()
        );
    }


    void sendWarning(@NotNull CommandSender sender, @NotNull String warning) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(warning).color(NamedTextColor.YELLOW))
                .build()
        );
    }
}
