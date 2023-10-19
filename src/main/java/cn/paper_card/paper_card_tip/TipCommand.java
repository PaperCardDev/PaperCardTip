package cn.paper_card.paper_card_tip;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

class TipCommand extends TheMcCommand.HasSub {

    private final @NotNull PaperCardTip plugin;

    private final @NotNull Permission permission;

    TipCommand(@NotNull PaperCardTip plugin) {
        super("tip");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("paper-card-tip.command"));

        final PluginCommand command = plugin.getCommand(this.getLabel());
        assert command != null;
        command.setExecutor(this);
        command.setTabCompleter(this);

        this.addSubCommand(new Id());
        this.addSubCommand(new Add());
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
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                final TextComponent.Builder text = Component.text();
                text.append(Component.text("添加Tip成功，ID: %d".formatted(id)).color(NamedTextColor.GREEN));
                text.appendSpace();
                text.append(Component.text("[点击查看]")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/tip id %d".formatted(id)))
                );

                plugin.sendInfo(commandSender, text.build());
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
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (tip == null) {
                    plugin.sendWarning(commandSender, "以%d为ID的Tip不存在！".formatted(id));
                    return;
                }

                final TextComponent.Builder text = Component.text();
                text.append(Component.text("==== 以%d为ID的Tip ====".formatted(id)));
                text.appendNewline();
                plugin.appendTipInfo(text, tip);

                plugin.sendInfo(commandSender, text.build());
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

}
