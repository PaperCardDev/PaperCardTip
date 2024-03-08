package cn.paper_card.paper_card_tip;

import cn.paper_card.database.api.DatabaseApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

class TipApiImpl implements PaperCardTipApi {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Table table = null;
    private Connection connection = null;

    TipApiImpl(DatabaseApi.@NotNull MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
    }

    private @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon && this.table != null) return this.table;

        if (this.table != null) this.table.close();
        this.table = new Table(newCon);
        this.connection = newCon;
        return this.table;
    }

    void destroy() throws SQLException {
        synchronized (this.mySqlConnection) {
            final Table t = this.table;
            this.table = null;
            this.connection = null;
            if (t != null) t.close();
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
    public boolean deleteTip(int id) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int deleted = t.deleteTipById(id);
                this.mySqlConnection.setLastUseTime();

                if (deleted == 1) return true;
                if (deleted == 0) return false;
                throw new RuntimeException("根据一个ID删除了%d条数据！".formatted(deleted));
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
    public boolean updateTipById(@NotNull Tip tip) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.updateTipById(tip);
                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return true;
                if (updated == 0) return false;

                throw new RuntimeException("根据一个ID更新了%d条数据！".formatted(updated));
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
    public @Nullable Tip queryById(int id) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final List<Tip> tips = t.queryTipById(id);
                final int size = tips.size();

                if (size == 1) return tips.get(0);
                if (size == 0) return null;
                throw new RuntimeException("根据一个ID查询到了%d条数据！".formatted(size));
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
    public @NotNull List<Tip> queryByPage(int limit, int offset) throws SQLException {
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
    public boolean setPlayerIndex(@NotNull UUID uuid, int index) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final int updated = t.updatePlayerIndex(uuid, index);
                this.mySqlConnection.setLastUseTime();
                if (updated == 1) return false;
                if (updated == 0) {
                    final int inserted = t.insertPlayerIndex(uuid, index);
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));
                    return true;
                }
                throw new RuntimeException("更新了%d条数据！".formatted(updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }
}
