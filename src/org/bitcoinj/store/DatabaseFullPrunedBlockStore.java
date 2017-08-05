package org.bitcoinj.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;

/**
 * <p>A generic full pruned block store for a relational database.  This generic class
 * requires certain table structures for the block store.</p>
 *
 * <p>The following are the tables and field names/types that are assumed:</p>
 *
 * <p>
 * <b>setting</b> table
 * <table>
 *     <tr><th>Field Name</th><th>Type (generic)</th></tr>
 *     <tr><td>name</td><td>string</td></tr>
 *     <tr><td>value</td><td>binary</td></tr>
 * </table>
 * </p>
 *
 * <p><br/>
 * <b>headers</b> table
 * <table>
 *     <tr><th>Field Name</th><th>Type (generic)</th></tr>
 *     <tr><td>hash</td><td>binary</td></tr>
 *     <tr><td>chainwork</td><td>binary</td></tr>
 *     <tr><td>height</td><td>integer</td></tr>
 *     <tr><td>header</td><td>binary</td></tr>
 *     <tr><td>wasundoable</td><td>boolean</td></tr>
 * </table>
 * </p>
 *
 * <p><br/>
 * <b>undoableblocks</b> table
 * <table>
 *     <tr><th>Field Name</th><th>Type (generic)</th></tr>
 *     <tr><td>hash</td><td>binary</td></tr>
 *     <tr><td>height</td><td>integer</td></tr>
 *     <tr><td>txoutchanges</td><td>binary</td></tr>
 *     <tr><td>transactions</td><td>binary</td></tr>
 * </table>
 * </p>
 *
 * <p><br/>
 * <b>openoutputs</b> table
 * <table>
 *     <tr><th>Field Name</th><th>Type (generic)</th></tr>
 *     <tr><td>hash</td><td>binary</td></tr>
 *     <tr><td>index</td><td>integer</td></tr>
 *     <tr><td>height</td><td>integer</td></tr>
 *     <tr><td>value</td><td>integer</td></tr>
 *     <tr><td>scriptbytes</td><td>binary</td></tr>
 *     <tr><td>toaddress</td><td>string</td></tr>
 *     <tr><td>addresstargetable</td><td>integer</td></tr>
 *     <tr><td>coinbase</td><td>boolean</td></tr>
 * </table>
 * </p>
 *
 */
public abstract class DatabaseFullPrunedBlockStore implements FullPrunedBlockStore
{
    private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

    private static final String CHAIN_HEAD_SETTING                = "chainhead";
    private static final String VERIFIED_CHAIN_HEAD_SETTING       = "verifiedchainhead";
    private static final String VERSION_SETTING                   = "version";

    // Drop table SQL.
    private static final String DROP_SETTINGS_TABLE               = "DROP TABLE settings";
    private static final String DROP_HEADERS_TABLE                = "DROP TABLE headers";
    private static final String DROP_UNDOABLE_TABLE               = "DROP TABLE undoableblocks";
    private static final String DROP_OPEN_OUTPUT_TABLE            = "DROP TABLE openoutputs";

    // Queries SQL.
    private static final String SELECT_SETTINGS_SQL               = "SELECT value FROM settings WHERE name = ?";
    private static final String INSERT_SETTINGS_SQL               = "INSERT INTO settings(name, value) VALUES(?, ?)";
    private static final String UPDATE_SETTINGS_SQL               = "UPDATE settings SET value = ? WHERE name = ?";

    private static final String SELECT_HEADERS_SQL                = "SELECT chainwork, height, header, wasundoable FROM headers WHERE hash = ?";
    private static final String INSERT_HEADERS_SQL                = "INSERT INTO headers(hash, chainwork, height, header, wasundoable) VALUES(?, ?, ?, ?, ?)";
    private static final String UPDATE_HEADERS_SQL                = "UPDATE headers SET wasundoable=? WHERE hash=?";

    private static final String SELECT_UNDOABLEBLOCKS_SQL         = "SELECT txoutchanges, transactions FROM undoableblocks WHERE hash = ?";
    private static final String INSERT_UNDOABLEBLOCKS_SQL         = "INSERT INTO undoableblocks(hash, height, txoutchanges, transactions) VALUES(?, ?, ?, ?)";
    private static final String UPDATE_UNDOABLEBLOCKS_SQL         = "UPDATE undoableblocks SET txoutchanges=?, transactions=? WHERE hash = ?";
    private static final String DELETE_UNDOABLEBLOCKS_SQL         = "DELETE FROM undoableblocks WHERE height <= ?";

    private static final String SELECT_OPENOUTPUTS_SQL            = "SELECT height, value, scriptbytes, coinbase, toaddress, addresstargetable FROM openoutputs WHERE hash = ? AND index = ?";
    private static final String SELECT_OPENOUTPUTS_COUNT_SQL      = "SELECT COUNT(*) FROM openoutputs WHERE hash = ?";
    private static final String INSERT_OPENOUTPUTS_SQL            = "INSERT INTO openoutputs (hash, index, height, value, scriptbytes, toaddress, addresstargetable, coinbase) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_OPENOUTPUTS_SQL            = "DELETE FROM openoutputs WHERE hash = ? AND index = ?";

    // Dump table SQL (this is just for data sizing statistics).
    private static final String SELECT_DUMP_SETTINGS_SQL          = "SELECT name, value FROM settings";
    private static final String SELECT_DUMP_HEADERS_SQL           = "SELECT chainwork, header FROM headers";
    private static final String SELECT_DUMP_UNDOABLEBLOCKS_SQL    = "SELECT txoutchanges, transactions FROM undoableblocks";
    private static final String SELECT_DUMP_OPENOUTPUTS_SQL       = "SELECT value, scriptbytes FROM openoutputs";

    private static final String SELECT_TRANSACTION_OUTPUTS_SQL    = "SELECT hash, value, scriptbytes, height, index, coinbase, toaddress, addresstargetable FROM openoutputs where toaddress = ?";

    // Select the balance of an address SQL.
    private static final String SELECT_BALANCE_SQL                = "select sum(value) from openoutputs where toaddress = ?";

    // Tables exist SQL.
    private static final String SELECT_CHECK_TABLES_EXIST_SQL     = "SELECT * FROM settings WHERE 1 = 2";

    // Compatibility SQL.
    private static final String SELECT_COMPATIBILITY_COINBASE_SQL = "SELECT coinbase FROM openoutputs WHERE 1 = 2";

    protected Sha256Hash chainHeadHash;
    protected StoredBlock chainHeadBlock;
    protected Sha256Hash verifiedChainHeadHash;
    protected StoredBlock verifiedChainHeadBlock;
    protected NetworkParameters params;
    protected ThreadLocal<Connection> conn;
    protected List<Connection> allConnections;
    protected String connectionURL;
    protected int fullStoreDepth;
    protected String username;
    protected String password;
    protected String schemaName;

    /**
     * <p>Create a new DatabaseFullPrunedBlockStore, using the full connection URL instead of a hostname and password,
     * and optionally allowing a schema to be specified.</p>
     *
     * @param params A copy of the NetworkParameters used.
     * @param connectionURL The jdbc url to connect to the database.
     * @param fullStoreDepth The number of blocks of history stored in full (something like 1000 is pretty safe).
     * @param username The database username.
     * @param password The password to the database.
     * @param schemaName The name of the schema to put the tables in.  May be null if no schema is being used.
     * @throws BlockStoreException if there is a failure to connect and/or initialise the database.
     */
    public DatabaseFullPrunedBlockStore(NetworkParameters params, String connectionURL, int fullStoreDepth, @Nullable String username, @Nullable String password, @Nullable String schemaName)
        throws BlockStoreException
    {
        this.params = params;
        this.fullStoreDepth = fullStoreDepth;
        this.connectionURL = connectionURL;
        this.schemaName = schemaName;
        this.username = username;
        this.password = password;
        this.conn = new ThreadLocal<>();
        this.allConnections = new LinkedList<>();

        try
        {
            Class.forName(getDatabaseDriverClass());
            log.info(getDatabaseDriverClass() + " loaded. ");
        }
        catch (ClassNotFoundException e)
        {
            log.error("check CLASSPATH for database driver jar ", e);
        }

        maybeConnect();

        try
        {
            // Create tables if needed.
            if (!tablesExist())
                createTables();
            else
                checkCompatibility();
            initFromDatabase();
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    /**
     * Get the database driver class, i.e <i>org.postgresql.Driver</i>.
     * @return the fully qualified database driver class.
     */
    protected abstract String getDatabaseDriverClass();

    /**
     * Get the SQL statements that create the schema (DDL).
     * @return the list of SQL statements.
     */
    protected abstract List<String> getCreateSchemeSQL();

    /**
     * Get the SQL statements that create the tables (DDL).
     * @return the list of SQL statements.
     */
    protected abstract List<String> getCreateTablesSQL();

    /**
     * Get the SQL statements that create the indexes (DDL).
     * @return the list of SQL statements.
     */
    protected abstract List<String> getCreateIndexesSQL();

    /**
     * Get the database specific error code that indicated a duplicate key error when inserting a record.
     * <p>This is the code returned by {@link java.sql.SQLException#getSQLState()}.</p>
     * @return the database duplicate error code.
     */
    protected abstract String getDuplicateKeyErrorCode();

    /**
     * Get the SQL to select the total balance for a given address.
     * @return the SQL prepared statement.
     */
    protected String getBalanceSelectSQL()
    {
        return SELECT_BALANCE_SQL;
    }

    /**
     * Get the SQL statement that checks if tables exist.
     * @return the SQL prepared statement.
     */
    protected String getTablesExistSQL()
    {
        return SELECT_CHECK_TABLES_EXIST_SQL;
    }

    /**
     * Get the SQL statements to check if the database is compatible.
     * @return the SQL prepared statements.
     */
    protected List<String> getCompatibilitySQL()
    {
        List<String> sql = new ArrayList<>();
        sql.add(SELECT_COMPATIBILITY_COINBASE_SQL);
        return sql;
    }

    /**
     * Get the SQL to select the transaction outputs for a given address.
     * @return the SQL prepared statement.
     */
    protected String getTransactionOutputSelectSQL()
    {
        return SELECT_TRANSACTION_OUTPUTS_SQL;
    }

    /**
     * Get the SQL to drop all the tables (DDL).
     * @return the SQL drop statements.
     */
    protected List<String> getDropTablesSQL()
    {
        List<String> sql = new ArrayList<>();
        sql.add(DROP_SETTINGS_TABLE);
        sql.add(DROP_HEADERS_TABLE);
        sql.add(DROP_UNDOABLE_TABLE);
        sql.add(DROP_OPEN_OUTPUT_TABLE);
        return sql;
    }

    /**
     * Get the SQL to select a setting value.
     * @return the SQL select statement.
     */
    protected String getSelectSettingsSQL()
    {
        return SELECT_SETTINGS_SQL;
    }

    /**
     * Get the SQL to insert a settings record.
     * @return the SQL insert statement.
     */
    protected String getInsertSettingsSQL()
    {
        return INSERT_SETTINGS_SQL;
    }

    /**
     * Get the SQL to update a setting value.
     * @return the SQL update statement.
     */
    protected String getUpdateSettingsSLQ()
    {
        return UPDATE_SETTINGS_SQL;
    }

    /**
     * Get the SQL to select a headers record.
     * @return the SQL select statement.
     */
    protected String getSelectHeadersSQL()
    {
        return SELECT_HEADERS_SQL;
    }

    /**
     * Get the SQL to insert a headers record.
     * @return the SQL insert statement.
     */
    protected String getInsertHeadersSQL()
    {
        return INSERT_HEADERS_SQL;
    }

    /**
     * Get the SQL to update a headers record.
     * @return the SQL update statement.
     */
    protected String getUpdateHeadersSQL()
    {
        return UPDATE_HEADERS_SQL;
    }

    /**
     * Get the SQL to select an undoableblocks record.
     * @return the SQL select statement.
     */
    protected String getSelectUndoableBlocksSQL()
    {
        return SELECT_UNDOABLEBLOCKS_SQL;
    }

    /**
     * Get the SQL to insert a undoableblocks record.
     * @return the SQL insert statement.
     */
    protected String getInsertUndoableBlocksSQL()
    {
        return INSERT_UNDOABLEBLOCKS_SQL;
    }

    /**
     * Get the SQL to update a undoableblocks record.
     * @return the SQL update statement.
     */
    protected String getUpdateUndoableBlocksSQL()
    {
        return UPDATE_UNDOABLEBLOCKS_SQL;
    }

    /**
     * Get the SQL to delete a undoableblocks record.
     * @return the SQL delete statement.
     */
    protected String getDeleteUndoableBlocksSQL()
    {
        return DELETE_UNDOABLEBLOCKS_SQL;
    }

    /**
     * Get the SQL to select a openoutputs record.
     * @return the SQL select statement.
     */
    protected String getSelectOpenoutputsSQL()
    {
        return SELECT_OPENOUTPUTS_SQL;
    }

    /**
     * Get the SQL to select count of openoutputs.
     * @return the SQL select statement.
     */
    protected String getSelectOpenoutputsCountSQL()
    {
        return SELECT_OPENOUTPUTS_COUNT_SQL;
    }

    /**
     * Get the SQL to insert a openoutputs record.
     * @return the SQL insert statement.
     */
    protected String getInsertOpenoutputsSQL()
    {
        return INSERT_OPENOUTPUTS_SQL;
    }

    /**
     * Get the SQL to delete a openoutputs record.
     * @return the SQL delete statement.
     */
    protected String getDeleteOpenoutputsSQL()
    {
        return DELETE_OPENOUTPUTS_SQL;
    }

    /**
     * Get the SQL to select the setting dump fields for sizing/statistics.
     * @return the SQL select statement.
     */
    protected String getSelectSettingsDumpSQL()
    {
        return SELECT_DUMP_SETTINGS_SQL;
    }

    /**
     * Get the SQL to select the headers dump fields for sizing/statistics.
     * @return the SQL select statement.
     */
    protected String getSelectHeadersDumpSQL()
    {
        return SELECT_DUMP_HEADERS_SQL;
    }

    /**
     * Get the SQL to select the undoableblocks dump fields for sizing/statistics.
     * @return the SQL select statement.
     */
    protected String getSelectUndoableblocksDumpSQL()
    {
        return SELECT_DUMP_UNDOABLEBLOCKS_SQL;
    }

    /**
     * Get the SQL to select the openoutouts dump fields for sizing/statistics.
     * @return the SQL select statement.
     */
    protected String getSelectopenoutputsDumpSQL()
    {
        return SELECT_DUMP_OPENOUTPUTS_SQL;
    }

    /**
     * <p>If there isn't a connection on the {@link ThreadLocal} then create and store it.</p>
     * <p>This will also automatically set up the schema if it does not exist within the DB.</p>
     * @throws BlockStoreException if successful connection to the DB couldn't be made.
     */
    protected synchronized final void maybeConnect()
        throws BlockStoreException
    {
        try
        {
            if (conn.get() != null && !conn.get().isClosed())
                return;

            if (username == null || password == null)
            {
                conn.set(DriverManager.getConnection(connectionURL));
            }
            else
            {
                Properties props = new Properties();
                props.setProperty("user", this.username);
                props.setProperty("password", this.password);
                conn.set(DriverManager.getConnection(connectionURL, props));
            }
            allConnections.add(conn.get());
            Connection connection = conn.get();
            // Set the schema if one is needed.
            if (schemaName != null)
            {
                Statement s = connection.createStatement();
                for (String sql : getCreateSchemeSQL())
                    s.execute(sql);
            }
            log.info("Made a new connection to database " + connectionURL);
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public synchronized void close()
    {
        for (Connection conn : allConnections)
        {
            try
            {
                if (!conn.getAutoCommit())
                    conn.rollback();
                conn.close();
                if (conn == this.conn.get())
                    this.conn.set(null);
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }
        allConnections.clear();
    }

    /**
     * <p>Check if a tables exists within the database.</p>
     *
     * <p>This specifically checks for the 'settings' table and
     * if it exists makes an assumption that the rest of the data
     * structures are present.</p>
     *
     * @return if the tables exist.
     * @throws java.sql.SQLException
     */
    private boolean tablesExist()
        throws SQLException
    {
        PreparedStatement ps = null;
        try
        {
            ps = conn.get().prepareStatement(getTablesExistSQL());
            ResultSet results = ps.executeQuery();
            results.close();
            return true;
        }
        catch (SQLException _)
        {
            return false;
        }
        finally
        {
            if (ps != null && !ps.isClosed())
                ps.close();
        }
    }

    /**
     * Check that the database is compatible with this version of the {@link DatabaseFullPrunedBlockStore}.
     * @throws BlockStoreException if the database is not compatible.
     */
    private void checkCompatibility()
        throws SQLException, BlockStoreException
    {
        for (String sql : getCompatibilitySQL())
        {
            PreparedStatement ps = null;
            try
            {
                ps = conn.get().prepareStatement(sql);
                ResultSet results = ps.executeQuery();
                results.close();
            }
            catch (SQLException e)
            {
                throw new BlockStoreException("Database block store is not compatible with the current release.  See bitcoinj release notes for further information: " + e.getMessage());
            }
            finally
            {
                if (ps != null && !ps.isClosed())
                    ps.close();
            }
        }
    }

    /**
     * Create the tables in the database.
     * @throws java.sql.SQLException if there is a database error.
     * @throws BlockStoreException if the block store could not be created.
     */
    private void createTables()
        throws SQLException, BlockStoreException
    {
        Statement s = conn.get().createStatement();
        // Create all the database tables.
        for (String sql : getCreateTablesSQL())
        {
            if (log.isDebugEnabled())
                log.debug("DatabaseFullPrunedBlockStore : CREATE table [SQL= {0}]", sql);
            s.executeUpdate(sql);
        }
        // Create all the database indexes.
        for (String sql : getCreateIndexesSQL())
        {
            if (log.isDebugEnabled())
                log.debug("DatabaseFullPrunedBlockStore : CREATE index [SQL= {0}]", sql);
            s.executeUpdate(sql);
        }
        s.close();

        // Insert the initial settings for this store.
        PreparedStatement ps = conn.get().prepareStatement(getInsertSettingsSQL());
        ps.setString(1, CHAIN_HEAD_SETTING);
        ps.setNull(2, Types.BINARY);
        ps.execute();
        ps.setString(1, VERIFIED_CHAIN_HEAD_SETTING);
        ps.setNull(2, Types.BINARY);
        ps.execute();
        ps.setString(1, VERSION_SETTING);
        ps.setBytes(2, "03".getBytes());
        ps.execute();
        ps.close();

        createNewStore(params);
    }

    /**
     * Create a new store for the given {@link org.bitcoinj.core.NetworkParameters}.
     * @param params The network.
     * @throws BlockStoreException if the store couldn't be created.
     */
    private void createNewStore(NetworkParameters params)
        throws BlockStoreException
    {
        try
        {
            // Set up the genesis block.  When we start out fresh, it is by definition the top of the chain.
            StoredBlock storedGenesisHeader = new StoredBlock(params.getGenesisBlock().cloneAsHeader(), params.getGenesisBlock().getWork(), 0);
            // The coinbase in the genesis block is not spendable.  This is because of how Bitcoin Core inits
            // its database - the genesis transaction isn't actually in the db so its spent flags can never be updated.
            List<Transaction> genesisTransactions = Lists.newLinkedList();
            StoredUndoableBlock storedGenesis = new StoredUndoableBlock(params.getGenesisBlock().getHash(), genesisTransactions);
            put(storedGenesisHeader, storedGenesis);
            setChainHead(storedGenesisHeader);
            setVerifiedChainHead(storedGenesisHeader);
        }
        catch (VerificationException e)
        {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    /**
     * Initialise the store state from the database.
     * @throws java.sql.SQLException if there is a database error.
     * @throws BlockStoreException if there is a block store error.
     */
    private void initFromDatabase()
        throws SQLException, BlockStoreException
    {
        PreparedStatement ps = conn.get().prepareStatement(getSelectSettingsSQL());
        ResultSet rs;
        ps.setString(1, CHAIN_HEAD_SETTING);
        rs = ps.executeQuery();
        if (!rs.next())
            throw new BlockStoreException("corrupt database block store - no chain head pointer");

        Sha256Hash hash = Sha256Hash.wrap(rs.getBytes(1));
        rs.close();
        this.chainHeadBlock = get(hash);
        this.chainHeadHash = hash;
        if (this.chainHeadBlock == null)
            throw new BlockStoreException("corrupt database block store - head block not found");

        ps.setString(1, VERIFIED_CHAIN_HEAD_SETTING);
        rs = ps.executeQuery();
        if (!rs.next())
            throw new BlockStoreException("corrupt database block store - no verified chain head pointer");

        hash = Sha256Hash.wrap(rs.getBytes(1));
        rs.close();
        ps.close();
        this.verifiedChainHeadBlock = get(hash);
        this.verifiedChainHeadHash = hash;
        if (this.verifiedChainHeadBlock == null)
            throw new BlockStoreException("corrupt database block store - verified head block not found");
    }

    protected void putUpdateStoredBlock(StoredBlock storedBlock, boolean wasUndoable)
        throws SQLException
    {
        try
        {
            PreparedStatement ps = conn.get().prepareStatement(getInsertHeadersSQL());
            // We skip the first 4 bytes because (on mainnet) the minimum target has 4 0-bytes.
            byte[] hashBytes = new byte[28];
            System.arraycopy(storedBlock.getHeader().getHash().getBytes(), 4, hashBytes, 0, 28);
            ps.setBytes(1, hashBytes);
            ps.setBytes(2, storedBlock.getChainWork().toByteArray());
            ps.setInt(3, storedBlock.getHeight());
            ps.setBytes(4, storedBlock.getHeader().cloneAsHeader().unsafeBitcoinSerialize());
            ps.setBoolean(5, wasUndoable);
            ps.executeUpdate();
            ps.close();
        }
        catch (SQLException e)
        {
            // It is possible we try to add a duplicate StoredBlock if we upgraded.
            // In that case, we just update the entry to mark it wasUndoable.
            if  (!(e.getSQLState().equals(getDuplicateKeyErrorCode())) || !wasUndoable)
                throw e;

            PreparedStatement ps = conn.get().prepareStatement(getUpdateHeadersSQL());
            ps.setBoolean(1, true);
            // We skip the first 4 bytes because (on mainnet) the minimum target has 4 0-bytes.
            byte[] hashBytes = new byte[28];
            System.arraycopy(storedBlock.getHeader().getHash().getBytes(), 4, hashBytes, 0, 28);
            ps.setBytes(2, hashBytes);
            ps.executeUpdate();
            ps.close();
        }
    }

    @Override
    public void put(StoredBlock storedBlock)
        throws BlockStoreException
    {
        maybeConnect();
        try
        {
            putUpdateStoredBlock(storedBlock, false);
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void put(StoredBlock storedBlock, StoredUndoableBlock undoableBlock)
        throws BlockStoreException
    {
        maybeConnect();
        // We skip the first 4 bytes because (on mainnet) the minimum target has 4 0-bytes.
        byte[] hashBytes = new byte[28];
        System.arraycopy(storedBlock.getHeader().getHash().getBytes(), 4, hashBytes, 0, 28);
        int height = storedBlock.getHeight();
        byte[] transactions = null;
        byte[] txOutChanges = null;
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (undoableBlock.getTxOutChanges() != null)
            {
                undoableBlock.getTxOutChanges().serializeToStream(bos);
                txOutChanges = bos.toByteArray();
            }
            else
            {
                int numTxn = undoableBlock.getTransactions().size();
                bos.write(0xff & numTxn);
                bos.write(0xff & (numTxn >> 8));
                bos.write(0xff & (numTxn >> 16));
                bos.write(0xff & (numTxn >> 24));
                for (Transaction tx : undoableBlock.getTransactions())
                    tx.bitcoinSerialize(bos);
                transactions = bos.toByteArray();
            }
            bos.close();
        }
        catch (IOException e)
        {
            throw new BlockStoreException(e);
        }

        try
        {
            try
            {
                PreparedStatement ps = conn.get().prepareStatement(getInsertUndoableBlocksSQL());
                ps.setBytes(1, hashBytes);
                ps.setInt(2, height);
                if (transactions == null)
                {
                    ps.setBytes(3, txOutChanges);
                    ps.setNull(4, Types.BINARY);
                }
                else
                {
                    ps.setNull(3, Types.BINARY);
                    ps.setBytes(4, transactions);
                }
                ps.executeUpdate();
                ps.close();
                try
                {
                    putUpdateStoredBlock(storedBlock, true);
                }
                catch (SQLException e)
                {
                    throw new BlockStoreException(e);
                }
            }
            catch (SQLException e)
            {
                if (!e.getSQLState().equals(getDuplicateKeyErrorCode()))
                    throw new BlockStoreException(e);

                // There is probably an update-or-insert statement, but it wasn't obvious from the docs.
                PreparedStatement ps = conn.get().prepareStatement(getUpdateUndoableBlocksSQL());
                ps.setBytes(3, hashBytes);
                if (transactions == null)
                {
                    ps.setBytes(1, txOutChanges);
                    ps.setNull(2, Types.BINARY);
                }
                else
                {
                    ps.setNull(1, Types.BINARY);
                    ps.setBytes(2, transactions);
                }
                ps.executeUpdate();
                ps.close();
            }
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    public StoredBlock get(Sha256Hash hash, boolean wasUndoableOnly)
        throws BlockStoreException
    {
        // Optimize for chain head.
        if (chainHeadHash != null && chainHeadHash.equals(hash))
            return chainHeadBlock;

        if (verifiedChainHeadHash != null && verifiedChainHeadHash.equals(hash))
            return verifiedChainHeadBlock;

        maybeConnect();
        PreparedStatement ps = null;
        try
        {
            ps = conn.get().prepareStatement(getSelectHeadersSQL());
            // We skip the first 4 bytes because (on mainnet) the minimum target has 4 0-bytes.
            byte[] hashBytes = new byte[28];
            System.arraycopy(hash.getBytes(), 4, hashBytes, 0, 28);
            ps.setBytes(1, hashBytes);
            ResultSet results = ps.executeQuery();
            if (!results.next())
                return null;

            // Parse it.
            if (wasUndoableOnly && !results.getBoolean(4))
                return null;

            BigInteger chainWork = new BigInteger(results.getBytes(1));
            int height = results.getInt(2);
            Block b = params.getDefaultSerializer().makeBlock(results.getBytes(3));
            b.verifyHeader();
            StoredBlock stored = new StoredBlock(b, chainWork, height);
            return stored;
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
        catch (ProtocolException e)
        {
            // Corrupted database.
            throw new BlockStoreException(e);
        }
        catch (VerificationException e)
        {
            // Should not be able to happen unless the database contains bad blocks.
            throw new BlockStoreException(e);
        }
        finally
        {
            if (ps != null)
            {
                try
                {
                    ps.close();
                }
                catch (SQLException _)
                {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public StoredBlock get(Sha256Hash hash)
        throws BlockStoreException
    {
        return get(hash, false);
    }

    @Override
    public StoredBlock getOnceUndoableStoredBlock(Sha256Hash hash)
        throws BlockStoreException
    {
        return get(hash, true);
    }

    @Override
    public StoredUndoableBlock getUndoBlock(Sha256Hash hash)
        throws BlockStoreException
    {
        maybeConnect();
        PreparedStatement ps = null;
        try
        {
            ps = conn.get().prepareStatement(getSelectUndoableBlocksSQL());
            // We skip the first 4 bytes because (on mainnet) the minimum target has 4 0-bytes.

            byte[] hashBytes = new byte[28];
            System.arraycopy(hash.getBytes(), 4, hashBytes, 0, 28);
            ps.setBytes(1, hashBytes);
            ResultSet results = ps.executeQuery();
            if (!results.next())
                return null;

            // Parse it.
            byte[] txOutChanges = results.getBytes(1);
            byte[] transactions = results.getBytes(2);
            StoredUndoableBlock block;
            if (txOutChanges == null)
            {
                int offset = 0;
                int numTxn = (transactions[offset++] & 0xff)
                          | ((transactions[offset++] & 0xff) << 8)
                          | ((transactions[offset++] & 0xff) << 16)
                          | ((transactions[offset++] & 0xff) << 24);
                List<Transaction> transactionList = new LinkedList<>();
                for (int i = 0; i < numTxn; i++)
                {
                    Transaction tx = params.getDefaultSerializer().makeTransaction(transactions, offset);
                    transactionList.add(tx);
                    offset += tx.getMessageSize();
                }
                block = new StoredUndoableBlock(hash, transactionList);
            }
            else
            {
                TransactionOutputChanges outChangesObject = new TransactionOutputChanges(new ByteArrayInputStream(txOutChanges));
                block = new StoredUndoableBlock(hash, outChangesObject);
            }
            return block;
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
        catch (NullPointerException e)
        {
            // Corrupted database.
            throw new BlockStoreException(e);
        }
        catch (ClassCastException e)
        {
            // Corrupted database.
            throw new BlockStoreException(e);
        }
        catch (ProtocolException e)
        {
            // Corrupted database.
            throw new BlockStoreException(e);
        }
        catch (IOException e)
        {
            // Corrupted database.
            throw new BlockStoreException(e);
        }
        finally
        {
            if (ps != null)
            {
                try
                {
                    ps.close();
                }
                catch (SQLException _)
                {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public StoredBlock getChainHead()
        throws BlockStoreException
    {
        return chainHeadBlock;
    }

    @Override
    public void setChainHead(StoredBlock chainHead)
        throws BlockStoreException
    {
        Sha256Hash hash = chainHead.getHeader().getHash();
        this.chainHeadHash = hash;
        this.chainHeadBlock = chainHead;
        maybeConnect();
        try
        {
            PreparedStatement ps = conn.get().prepareStatement(getUpdateSettingsSLQ());
            ps.setString(2, CHAIN_HEAD_SETTING);
            ps.setBytes(1, hash.getBytes());
            ps.executeUpdate();
            ps.close();
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public StoredBlock getVerifiedChainHead()
        throws BlockStoreException
    {
        return verifiedChainHeadBlock;
    }

    @Override
    public void setVerifiedChainHead(StoredBlock chainHead)
        throws BlockStoreException
    {
        Sha256Hash hash = chainHead.getHeader().getHash();
        this.verifiedChainHeadHash = hash;
        this.verifiedChainHeadBlock = chainHead;
        maybeConnect();
        try
        {
            PreparedStatement ps = conn.get().prepareStatement(getUpdateSettingsSLQ());
            ps.setString(2, VERIFIED_CHAIN_HEAD_SETTING);
            ps.setBytes(1, hash.getBytes());
            ps.executeUpdate();
            ps.close();
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
        if (this.chainHeadBlock.getHeight() < chainHead.getHeight())
            setChainHead(chainHead);
        removeUndoableBlocksWhereHeightIsLessThan(chainHead.getHeight() - fullStoreDepth);
    }

    private void removeUndoableBlocksWhereHeightIsLessThan(int height)
        throws BlockStoreException
    {
        try
        {
            PreparedStatement ps = conn.get().prepareStatement(getDeleteUndoableBlocksSQL());
            ps.setInt(1, height);
            if (log.isDebugEnabled())
                log.debug("Deleting undoable undoable block with height <= " + height);
            ps.executeUpdate();
            ps.close();
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public UTXO getTransactionOutput(Sha256Hash hash, long index)
        throws BlockStoreException
    {
        maybeConnect();
        PreparedStatement ps = null;
        try
        {
            ps = conn.get().prepareStatement(getSelectOpenoutputsSQL());
            ps.setBytes(1, hash.getBytes());
            // index is actually an unsigned int
            ps.setInt(2, (int)index);
            ResultSet results = ps.executeQuery();
            if (!results.next())
                return null;

            // Parse it.
            int height = results.getInt(1);
            Coin value = Coin.valueOf(results.getLong(2));
            byte[] scriptBytes = results.getBytes(3);
            boolean coinbase = results.getBoolean(4);
            String address = results.getString(5);
            return new UTXO(hash, index, value, height, coinbase, new Script(scriptBytes), address);
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
        finally
        {
            if (ps != null)
            {
                try
                {
                    ps.close();
                }
                catch (SQLException _)
                {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void addUnspentTransactionOutput(UTXO out)
        throws BlockStoreException
    {
        maybeConnect();
        PreparedStatement ps = null;
        try
        {
            ps = conn.get().prepareStatement(getInsertOpenoutputsSQL());
            ps.setBytes(1, out.getHash().getBytes());
            // index is actually an unsigned int
            ps.setInt(2, (int)out.getIndex());
            ps.setInt(3, out.getHeight());
            ps.setLong(4, out.getValue().value);
            ps.setBytes(5, out.getScript().getProgram());
            ps.setString(6, out.getAddress());
            ps.setInt(7, out.getScript().getScriptType().ordinal());
            ps.setBoolean(8, out.isCoinbase());
            ps.executeUpdate();
            ps.close();
        }
        catch (SQLException e)
        {
            if (!(e.getSQLState().equals(getDuplicateKeyErrorCode())))
                throw new BlockStoreException(e);
        }
        finally
        {
            if (ps != null)
            {
                try
                {
                    ps.close();
                }
                catch (SQLException e)
                {
                    throw new BlockStoreException(e);
                }
            }
        }
    }

    @Override
    public void removeUnspentTransactionOutput(UTXO out)
        throws BlockStoreException
    {
        maybeConnect();
        // TODO: This should only need one query (maybe a stored procedure).
        if (getTransactionOutput(out.getHash(), out.getIndex()) == null)
            throw new BlockStoreException("Tried to remove a UTXO from DatabaseFullPrunedBlockStore that it didn't have!");

        try
        {
            PreparedStatement ps = conn.get().prepareStatement(getDeleteOpenoutputsSQL());
            ps.setBytes(1, out.getHash().getBytes());
            // index is actually an unsigned int
            ps.setInt(2, (int)out.getIndex());
            ps.executeUpdate();
            ps.close();
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void beginDatabaseBatchWrite()
        throws BlockStoreException
    {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Starting database batch write with connection: " + conn.get().toString());

        try
        {
            conn.get().setAutoCommit(false);
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void commitDatabaseBatchWrite()
        throws BlockStoreException
    {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Committing database batch write with connection: " + conn.get().toString());

        try
        {
            conn.get().commit();
            conn.get().setAutoCommit(true);
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void abortDatabaseBatchWrite()
        throws BlockStoreException
    {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Rollback database batch write with connection: " + conn.get().toString());

        try
        {
            if (!conn.get().getAutoCommit())
            {
                conn.get().rollback();
                conn.get().setAutoCommit(true);
            }
            else
            {
                log.warn("Warning: Rollback attempt without transaction");
            }
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public boolean hasUnspentOutputs(Sha256Hash hash, int numOutputs)
        throws BlockStoreException
    {
        maybeConnect();
        PreparedStatement ps = null;
        try
        {
            ps = conn.get().prepareStatement(getSelectOpenoutputsCountSQL());
            ps.setBytes(1, hash.getBytes());
            ResultSet results = ps.executeQuery();
            if (!results.next())
                throw new BlockStoreException("Got no results from a COUNT(*) query");

            int count = results.getInt(1);
            return (count != 0);
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
        finally
        {
            if (ps != null)
            {
                try
                {
                    ps.close();
                }
                catch (SQLException _)
                {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public NetworkParameters getParams()
    {
        return params;
    }

    /**
     * Resets the store by deleting the contents of the tables and reinitialising them.
     * @throws BlockStoreException if the tables couldn't be cleared and initialised.
     */
    public void resetStore()
        throws BlockStoreException
    {
        maybeConnect();
        try
        {
            deleteStore();
            createTables();
            initFromDatabase();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes the store by deleting the tables within the database.
     * @throws BlockStoreException if tables couldn't be deleted.
     */
    public void deleteStore()
        throws BlockStoreException
    {
        maybeConnect();
        try
        {
            Statement s = conn.get().createStatement();
            for (String sql : getDropTablesSQL())
                s.execute(sql);
            s.close();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculate the balance for a coinbase, to-address, or p2sh address.
     *
     * <p>The balance {@link org.bitcoinj.store.DatabaseFullPrunedBlockStore#getBalanceSelectSQL()} returns
     * the balance (summed) as a number, then use calculateClientSide=false.</p>
     *
     * <p>The balance {@link org.bitcoinj.store.DatabaseFullPrunedBlockStore#getBalanceSelectSQL()} returns
     * all the open outputs as stored in the DB (binary), then use calculateClientSide=true.</p>
     *
     * @param address The address to calculate the balance of.
     * @return The balance of the address supplied.  If the address has not been seen,
     *         or there are no outputs open for this address, the return value is 0.
     * @throws BlockStoreException if there is an error getting the balance.
     */
    public BigInteger calculateBalanceForAddress(Address address)
        throws BlockStoreException
    {
        maybeConnect();
        PreparedStatement ps = null;
        try
        {
            ps = conn.get().prepareStatement(getBalanceSelectSQL());
            ps.setString(1, address.toString());
            ResultSet rs = ps.executeQuery();
            BigInteger balance = BigInteger.ZERO;
            return rs.next() ? BigInteger.valueOf(rs.getLong(1)) : balance;
        }
        catch (SQLException e)
        {
            throw new BlockStoreException(e);
        }
        finally
        {
            if (ps != null)
            {
                try
                {
                    ps.close();
                }
                catch (SQLException _)
                {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }
}
