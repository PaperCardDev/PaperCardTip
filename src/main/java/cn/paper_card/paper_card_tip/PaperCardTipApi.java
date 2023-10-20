package cn.paper_card.paper_card_tip;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface PaperCardTipApi {

    record Tip(
            int id,
            String content,
            String category
    ) {
    }

    // 添加
    int addTip(@NotNull Tip tip) throws Exception;

    // 删除
    boolean deleteTip(int id) throws Exception;

    // 修改
    boolean updateTipById(@NotNull Tip tip) throws Exception;

    // 根据ID查询
    @Nullable Tip queryById(int id) throws Exception;

    // 分页查询
    @NotNull List<Tip> queryByPage(int limit, int offset) throws Exception;

    // 查询总数
    int queryCount() throws Exception;

    // 查询玩家index，默认值为1
    int queryPlayerIndex(@NotNull UUID uuid) throws Exception;

    // 设置玩家的index
    void setPlayerIndex(@NotNull UUID uuid, int index) throws Exception;

}
