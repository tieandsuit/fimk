package nxt;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.EntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PhasingVote {

    private static final DbKey.LinkKeyFactory<PhasingVote> voteDbKeyFactory =
            new DbKey.LinkKeyFactory<PhasingVote>("transaction_id", "voter_id") {
                @Override
                public DbKey newKey(PhasingVote vote) {
                    return vote.dbKey;
                }
            };

    private static final EntityDbTable<PhasingVote> phasingVoteTable = new EntityDbTable<PhasingVote>("phasing_vote", voteDbKeyFactory) {

        @Override
        protected PhasingVote load(Connection con, ResultSet rs) throws SQLException {
            return new PhasingVote(rs);
        }

        @Override
        protected void save(Connection con, PhasingVote vote) throws SQLException {
            vote.save(con);
        }

    };

    public static DbIterator<PhasingVote> getTransactionVotes(long pendingTransactionId, int from, int to) {
        return phasingVoteTable.getManyBy(new DbClause.LongClause("transaction_id", pendingTransactionId), from, to);
    }

    public static PhasingVote getVote(long pendingTransactionId, long voterId) {
        return phasingVoteTable.get(voteDbKeyFactory.newKey(pendingTransactionId, voterId));
    }

    public static long countVotes(PhasingPoll poll) {
        if (poll.getDefaultVoteWeighting().isBalanceIndependent()) {
            return phasingVoteTable.getCount(new DbClause.LongClause("transaction_id", poll.getId()));
        }
        long cumulativeWeight = 0;
        try (DbIterator<PhasingVote> votes = PhasingVote.getTransactionVotes(poll.getId(), 0, Integer.MAX_VALUE)) {
            for (PhasingVote vote : votes) {
                cumulativeWeight += poll.getDefaultVoteWeighting().calcWeight(vote.getVoterId(), Math.min(poll.getFinishHeight(), Nxt.getBlockchain().getHeight()));
            }
        }
        return cumulativeWeight;
    }

    static void addVote(PhasingPoll poll, Transaction transaction) {
        phasingVoteTable.insert(new PhasingVote(transaction, poll.getId()));
    }

    static void init() {
    }

    private final long id;
    private final DbKey dbKey;
    private final long pendingTransactionId;
    private final long voterId;

    private PhasingVote(Transaction transaction, long pendingTransactionId) {
        this.id = transaction.getId();
        this.pendingTransactionId = pendingTransactionId;
        this.voterId = transaction.getSenderId();
        this.dbKey = voteDbKeyFactory.newKey(this.pendingTransactionId, this.voterId);
    }

    private PhasingVote(ResultSet rs) throws SQLException {
        this.id = rs.getLong("id");
        this.pendingTransactionId = rs.getLong("transaction_id");
        this.voterId = rs.getLong("voter_id");
        this.dbKey = voteDbKeyFactory.newKey(this.pendingTransactionId, this.voterId);
    }

    public long getId() {
        return id;
    }

    public long getPendingTransactionId() {
        return pendingTransactionId;
    }

    public long getVoterId() {
        return voterId;
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_vote (id, transaction_id, "
                + "voter_id, height) VALUES (?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.getId());
            pstmt.setLong(++i, this.getPendingTransactionId());
            pstmt.setLong(++i, this.getVoterId());
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

}
