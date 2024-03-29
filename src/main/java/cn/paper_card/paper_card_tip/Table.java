package cn.paper_card.paper_card_tip;

import cn.paper_card.database.api.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


// USE MYSQL
class Table {
    private static final String NAME_TIPS = "tip";
    private static final String NAME_TIP_INDEX = "tip_index";

    private final @NotNull Connection connection;

    private PreparedStatement stmtInsertTip = null;
    private PreparedStatement stmtDeleteTip = null;

    private PreparedStatement stmtUpdateTip = null;

    private PreparedStatement stmtQueryTipById = null;

    private PreparedStatement stmtQueryByPage = null;

    private PreparedStatement stmtQueryCount = null;

    //
    private PreparedStatement stmtInsertIndex = null;

    private PreparedStatement stmtUpdateIndex = null;

    private PreparedStatement stmtQueryIndex = null;

    Table(@NotNull Connection connection) throws SQLException {
        this.connection = connection;
        this.create1();
        this.create2();
    }

    private void create1() throws SQLException {
        Util.executeSQL(this.connection, """
                CREATE TABLE IF NOT EXISTS %s (
                      id INT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                      content VARCHAR(512) NOT NULL,
                      category VARCHAR(24) NOT NULL
                )""".formatted(NAME_TIPS));
    }

    private void create2() throws SQLException {
        Util.executeSQL(this.connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    uid1 BIGINT NOT NULL,
                    uid2 BIGINT NOT NULL,
                    i INT UNSIGNED NOT NULL
                )""".formatted(NAME_TIP_INDEX));
    }


    void close() throws SQLException {
        Util.closeAllStatements(this.getClass(), this);
    }

    private @NotNull PreparedStatement getStmtInsertTip() throws SQLException {
        if (this.stmtInsertTip == null) {
            this.stmtInsertTip = this.connection.prepareStatement
                    ("INSERT INTO %s (content,category) VALUES (?,?)".formatted(NAME_TIPS), PreparedStatement.RETURN_GENERATED_KEYS);
        }
        return this.stmtInsertTip;
    }

    private @NotNull PreparedStatement getStmtDeleteTip() throws SQLException {
        if (this.stmtDeleteTip == null) {
            this.stmtDeleteTip = this.connection.prepareStatement
                    ("DELETE FROM %s WHERE id=?".formatted(NAME_TIPS));
        }
        return this.stmtDeleteTip;
    }

    private @NotNull PreparedStatement getStmtUpdateTip() throws SQLException {
        if (this.stmtUpdateTip == null) {
            this.stmtUpdateTip = this.connection.prepareStatement
                    ("UPDATE %s SET content=?,category=? WHERE id=?".formatted(NAME_TIPS));
        }
        return this.stmtUpdateTip;
    }

    private @NotNull PreparedStatement getStmtQueryTipById() throws SQLException {
        if (this.stmtQueryTipById == null) {
            this.stmtQueryTipById = this.connection.prepareStatement
                    ("SELECT id,content,category FROM %s WHERE id=?".formatted(NAME_TIPS));
        }
        return this.stmtQueryTipById;
    }

    private @NotNull PreparedStatement getStmtQueryByPage() throws SQLException {
        if (this.stmtQueryByPage == null) {
            this.stmtQueryByPage = this.connection.prepareStatement("""
                    SELECT id, content, category
                    FROM %s
                    ORDER BY category
                    LIMIT ? OFFSET ?;""".formatted(NAME_TIPS));
        }
        return this.stmtQueryByPage;
    }

    private @NotNull PreparedStatement getStmtQueryCount() throws SQLException {
        if (this.stmtQueryCount == null) {
            this.stmtQueryCount = this.connection.prepareStatement
                    ("SELECT count(*) FROM %s".formatted(NAME_TIPS));
        }
        return this.stmtQueryCount;
    }

    private @NotNull PreparedStatement getStmtInsertIndex() throws SQLException {
        if (this.stmtInsertIndex == null) {
            this.stmtInsertIndex = this.connection.prepareStatement
                    ("INSERT INTO %s (uid1,uid2,i) VALUES (?,?,?)".formatted(NAME_TIP_INDEX));
        }
        return this.stmtInsertIndex;
    }

    private @NotNull PreparedStatement getStmtUpdateIndex() throws SQLException {
        if (this.stmtUpdateIndex == null) {
            this.stmtUpdateIndex = this.connection.prepareStatement
                    ("UPDATE %s SET i=? WHERE uid1=? AND uid2=?".formatted(NAME_TIP_INDEX));
        }
        return this.stmtUpdateIndex;
    }

    private @NotNull PreparedStatement getStmtQueryIndex() throws SQLException {
        if (this.stmtQueryIndex == null) {
            this.stmtQueryIndex = this.connection.prepareStatement
                    ("SELECT i FROM %s WHERE uid1=? AND uid2=?".formatted(NAME_TIP_INDEX));
        }
        return this.stmtQueryIndex;
    }

    private @NotNull List<PaperCardTipApi.Tip> parseAllTips(@NotNull ResultSet resultSet) throws SQLException {

        final List<PaperCardTipApi.Tip> tips = new LinkedList<>();

        try {
            while (resultSet.next()) {
                final int id = resultSet.getInt(1);
                final String content = resultSet.getString(2);
                final String category = resultSet.getString(3);
                final PaperCardTipApi.Tip tip = new PaperCardTipApi.Tip(id, content, category);
                tips.add(tip);
            }
        } catch (SQLException e) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }

        resultSet.close();

        return tips;
    }

    int insertTip(@NotNull PaperCardTipApi.Tip tip) throws SQLException {
        final PreparedStatement ps = this.getStmtInsertTip();
        ps.setString(1, tip.content());
        ps.setString(2, tip.category());
        ps.executeUpdate();
        final ResultSet generatedKeys = ps.getGeneratedKeys();

        final int id;
        try {
            if (generatedKeys.next()) {
                id = generatedKeys.getInt(1);
            } else throw new SQLException("不应该没有数据！");
            if (generatedKeys.next()) throw new SQLException("不应该还有数据！");

        } catch (SQLException e) {
            try {
                generatedKeys.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }
        generatedKeys.close();
        return id;
    }

    int deleteTipById(int id) throws SQLException {
        final PreparedStatement ps = this.getStmtDeleteTip();
        ps.setInt(1, id);
        return ps.executeUpdate();
    }

    int updateTipById(@NotNull PaperCardTipApi.Tip tip) throws SQLException {
        final PreparedStatement ps = this.getStmtUpdateTip();
        ps.setString(1, tip.content());
        ps.setString(2, tip.category());
        ps.setInt(3, tip.id());
        return ps.executeUpdate();
    }

    @NotNull List<PaperCardTipApi.Tip> queryTipById(int id) throws SQLException {
        final PreparedStatement ps = this.getStmtQueryTipById();
        ps.setInt(1, id);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseAllTips(resultSet);
    }

    @NotNull List<PaperCardTipApi.Tip> queryByPage(int limit, int offset) throws SQLException {
        final PreparedStatement ps = this.getStmtQueryByPage();
        ps.setInt(1, limit);
        ps.setInt(2, offset);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseAllTips(resultSet);
    }

    int queryCount() throws SQLException {
        final PreparedStatement ps = this.getStmtQueryCount();
        final ResultSet resultSet = ps.executeQuery();

        final int n;

        try {
            if (resultSet.next()) {
                n = resultSet.getInt(1);
            } else throw new SQLException("不应该没有数据！");

            if (resultSet.next()) throw new SQLException("不应该还有数据！");
        } catch (SQLException e) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }
        resultSet.close();
        return n;
    }

    int insertPlayerIndex(@NotNull UUID uuid, int i) throws SQLException {
        final PreparedStatement ps = this.getStmtInsertIndex();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setInt(3, i);
        return ps.executeUpdate();
    }

    int updatePlayerIndex(@NotNull UUID uuid, int i) throws SQLException {
        final PreparedStatement ps = this.getStmtUpdateIndex();
        ps.setInt(1, i);
        ps.setLong(2, uuid.getMostSignificantBits());
        ps.setLong(3, uuid.getLeastSignificantBits());
        return ps.executeUpdate();
    }

    @Nullable Integer queryPlayerIndex(@NotNull UUID uuid) throws SQLException {
        final PreparedStatement ps = this.getStmtQueryIndex();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());

        final ResultSet resultSet = ps.executeQuery();
        final Integer i;
        try {
            if (resultSet.next()) {
                i = resultSet.getInt(1);
            } else i = null;

            if (resultSet.next()) throw new SQLException("不应该还有数据！");

        } catch (SQLException e) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }
        resultSet.close();

        return i;
    }
}
