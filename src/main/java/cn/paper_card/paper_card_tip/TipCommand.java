package cn.paper_card.paper_card_tip;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.*;

class TipCommand extends TheMcCommand.HasSub {

    private final @NotNull PaperCardTip plugin;

    private final @NotNull Permission permission;

    private final @NotNull HashMap<UUID, PaperCardTipApi.Tip> toDeletes;

    TipCommand(@NotNull PaperCardTip plugin) {
        super("tip");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("paper-card-tip.command"));

        final PluginCommand command = plugin.getCommand(this.getLabel());
        assert command != null;
        command.setExecutor(this);
        command.setTabCompleter(this);

        this.toDeletes = new HashMap<>();

        this.addSubCommand(new ListAll());
        this.addSubCommand(new Id());
        this.addSubCommand(new Add());
        this.addSubCommand(new Update());
        this.addSubCommand(new Delete());
        this.addSubCommand(new DeleteConfirmCancel(true));
        this.addSubCommand(new DeleteConfirmCancel(false));
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    class Add extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Add() {
            super("add");
            this.permission = plugin.addPermission(TipCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // <内容> <分类>
            final String argContent = strings.length > 0 ? strings[0] : null;
            final String argCategory = strings.length > 1 ? strings[1] : null;

            if (argContent == null) {
                plugin.sendError(commandSender, "你必须提供参数：Tip的内容");
                return true;
            }

            if (argCategory == null) {
                plugin.sendError(commandSender, "你必须提供参数：Tip的分类");
                return true;
            }

            if (strings.length != 2) {
                plugin.sendError(commandSender, "只需要两个参数，你提供了%d个参数，是不是参数里有空格？".formatted(strings.length));
                return true;
            }


            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final PaperCardTipApi.Tip tip = new PaperCardTipApi.Tip(0, argContent, argCategory);
                final int id;

                try {
                    id = plugin.addTip(tip);
                } catch (SQLException e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.append(Component.text(" 添加Tip成功，ID: %d".formatted(id)));
                text.appendSpace();
                text.append(Component.text("[点击查看]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/tip id %d".formatted(id)))
                );
                commandSender.sendMessage(text.build().color(NamedTextColor.GREEN));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                if (arg.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<内容>");
                    return list;
                }
                return null;
            }

            if (strings.length == 2) {
                final String arg = strings[1];
                if (arg.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<分类>");
                    return list;
                }
                return null;
            }

            return null;
        }
    }

    class Update extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Update() {
            super("update");
            this.permission = plugin.addPermission(TipCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // <id> <内容> <分类>
            final String argId = strings.length > 0 ? strings[0] : null;
            final String argContent = strings.length > 1 ? strings[1] : null;
            final String argCategory = strings.length > 2 ? strings[2] : null;

            if (argId == null) {
                plugin.sendError(commandSender, "你必须提供参数：ID");
                return true;
            }

            if (argContent == null) {
                plugin.sendError(commandSender, "你必须提供参数：内容");
                return true;
            }

            if (argCategory == null) {
                plugin.sendError(commandSender, "你必须提供参数：分类");
                return true;
            }

            if (strings.length != 3) {
                plugin.sendError(commandSender, "只需要3个参数，而你提供了%d个参数，参数里有空格？".formatted(strings.length));
                return true;
            }

            final int id;

            try {
                id = Integer.parseInt(argId);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的ID".formatted(argId));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final boolean updated;

                try {
                    updated = plugin.updateTipById(new PaperCardTipApi.Tip(id, argContent, argCategory));
                } catch (Exception e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (!updated) {
                    plugin.sendWarning(commandSender, "更新失败，可能以%d为ID的TIp不存在，请直接添加".formatted(id));
                    return;
                }

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.append(Component.text(" 已修改ID为%d的Tip".formatted(id)));
                text.appendSpace();
                text.append(Component.text("[查看]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/tip id %d".formatted(id)))
                        .hoverEvent(HoverEvent.showText(Component.text("点击查看")))
                );

                commandSender.sendMessage(text.build().color(NamedTextColor.GREEN));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argId = strings[0];
                if (argId.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<ID>");
                    return list;
                }
                return null;
            }

            if (strings.length == 2) {
                final String argContent = strings[1];
                if (argContent.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<内容>");
                    return list;
                }
                return null;
            }

            if (strings.length == 3) {
                final String argCategory = strings[2];
                if (argCategory.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<分类>");
                    return list;
                }
                return null;
            }

            return null;
        }
    }

    class Delete extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Delete() {
            super("delete");
            this.permission = plugin.addPermission(TipCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            final String argId = strings.length > 0 ? strings[0] : null;

            if (argId == null) {
                plugin.sendError(commandSender, "你必须提供参数：Tip的ID");
                return true;
            }

            final int id;

            try {
                id = Integer.parseInt(argId);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的ID".formatted(argId));
                return true;
            }


            if (!(commandSender instanceof final Player player)) {
                plugin.sendError(commandSender, "该命令只能由玩家来执行");
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final PaperCardTipApi.Tip tip;

                try {
                    tip = plugin.queryById(id);
                } catch (Exception e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (tip == null) {
                    plugin.sendWarning(commandSender, "以%d为ID的Tip不存在！".formatted(id));
                    return;
                }

                // 添加到等到确认列表中
                synchronized (toDeletes) {
                    toDeletes.put(player.getUniqueId(), tip);
                }

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.append(Component.text(" 确认删除Tip？").color(NamedTextColor.YELLOW));

                text.appendNewline();
                plugin.appendTipInfo(text, tip);

                text.appendNewline();
                text.append(Component.text("[确认]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/tip delete-confirm"))
                );
                text.appendSpace();
                text.append(Component.text("[取消]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/tip delete-cancel"))
                );

                commandSender.sendMessage(text.build().color(NamedTextColor.GREEN));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                if (arg.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<ID>");
                    return list;
                }
            }
            return null;
        }
    }

    class DeleteConfirmCancel extends TheMcCommand {

        private final boolean isConfirm;
        private final @NotNull Permission permission;

        protected DeleteConfirmCancel(boolean isConfirm) {
            super(isConfirm ? "delete-confirm" : "delete-cancel");
            this.isConfirm = isConfirm;
            this.permission = plugin.addPermission(TipCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (!(commandSender instanceof final Player player)) {
                plugin.sendError(commandSender, "该命令只能由玩家来执行");
                return true;
            }

            final PaperCardTipApi.Tip tip;
            synchronized (toDeletes) {
                tip = toDeletes.remove(player.getUniqueId());
            }

            final String op = this.isConfirm ? "确认" : "取消";

            if (tip == null) {
                plugin.sendWarning(commandSender, "没有要" + op + "删除的Tip");
                return true;
            }

            if (!this.isConfirm) {
                plugin.sendInfo(commandSender, "已取消删除");
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final boolean deleted;

                try {
                    deleted = plugin.deleteTip(tip.id());
                } catch (Exception e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (!deleted) {
                    plugin.sendWarning(commandSender, "未知原因导致的删除失败");
                    return;
                }
                plugin.sendInfo(commandSender, "删除ID为%d的Tip".formatted(tip.id()));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    class Id extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Id() {
            super("id");
            this.permission = plugin.addPermission(TipCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argId = strings.length > 0 ? strings[0] : null;

            if (argId == null) {
                plugin.sendError(commandSender, "你必须提供参数：Tip的ID");
                return true;
            }

            final int id;

            try {
                id = Integer.parseInt(argId);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的ID".formatted(argId));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final PaperCardTipApi.Tip tip;

                try {
                    tip = plugin.queryById(id);
                } catch (Exception e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (tip == null) {
                    plugin.sendWarning(commandSender, "以%d为ID的Tip不存在！".formatted(id));
                    return;
                }

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.appendSpace();
                text.append(Component.text("==== 以%d为ID的Tip ====".formatted(id)));

                text.appendNewline();
                plugin.appendTipInfo(text, tip);

                commandSender.sendMessage(text.build().color(NamedTextColor.GREEN));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                if (arg.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<Tip的ID>");
                    return list;
                }
                return null;
            }
            return null;
        }
    }

    class ListAll extends TheMcCommand {

        private final @NotNull Permission permission;

        protected ListAll() {
            super("list");
            this.permission = plugin.addPermission(TipCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argPage = strings.length > 0 ? strings[0] : null;

            final int pageNo;

            if (argPage == null) {
                pageNo = 1;
            } else {
                try {
                    pageNo = Integer.parseInt(argPage);
                } catch (NumberFormatException e) {
                    plugin.sendError(commandSender, "%s 不是正确的页码".formatted(argPage));
                    return true;
                }
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final int pageSize = 4;
                final List<PaperCardTipApi.Tip> list;

                try {
                    list = plugin.queryByPage(pageSize, (pageNo - 1) * pageSize);
                } catch (Exception e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                final int size = list.size();

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);

                text.appendSpace();
                text.append(Component.text("==== Tips | 第%d页 ====".formatted(pageNo)).color(NamedTextColor.GREEN));


                text.appendNewline();
                if (size == 0) {
                    text.append(Component.text("本页没有任何记录啦").color(NamedTextColor.GRAY));
                } else {
                    text.append(Component.text("ID | 内容 | 分类 | 操作").color(NamedTextColor.GRAY));

                    for (PaperCardTipApi.Tip tip : list) {
                        text.appendNewline();
                        text.append(Component.text(tip.id()).color(NamedTextColor.GRAY));
                        text.append(Component.text(" | "));

                        text.append(Component.text(tip.content()).color(NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.copyToClipboard(tip.content()))
                                .hoverEvent(HoverEvent.showText(Component.text("点击复制")))
                        );
                        text.append(Component.text(" | "));

                        text.append(Component.text(tip.category()).color(NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.copyToClipboard(tip.category()))
                                .hoverEvent(HoverEvent.showText(Component.text("点击复制")))
                        );
                        text.append(Component.text(" | "));

                        text.append(Component.text("[修改]")
                                .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.suggestCommand("/tip update %d ".formatted(tip.id())))
                        );
                        text.appendSpace();

                        text.append(Component.text("[删除]")
                                .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/tip delete %d".formatted(tip.id())))
                        );
                    }
                }

                final boolean hasPre = pageNo > 1;
                final boolean noNext = size < pageSize;

                text.appendNewline();
                text.append(Component.text("[上一页]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(hasPre ? ClickEvent.runCommand("/tip list %d".formatted(pageNo - 1)) : null)
                        .hoverEvent(HoverEvent.showText(Component.text(hasPre ? "点击上一页" : "没有上一页啦")))
                );

                text.appendSpace();
                text.append(Component.text("[下一页]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(noNext ? null : ClickEvent.runCommand("/tip list %d".formatted(pageNo + 1)))
                        .hoverEvent(HoverEvent.showText(Component.text(noNext ? "没有下一页啦" : "点击下一页")))
                );

                commandSender.sendMessage(text.build().color(NamedTextColor.GREEN));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

}
