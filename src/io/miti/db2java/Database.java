package io.miti.db2java;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Encapsulate database operations.
 * 
 * @author mwallace
 * @version 1.0
 */
public final class Database
{
  /**
   * The current database connection.
   */
  private static Connection conn = null;
  
  /**
   * Whether we have attempted to retrieve a database
   * connection object.  If true, and conn = null,
   * there was an error.
   */
  private static boolean bRetrievedConnection = false;
  
  /**
   * Whether the connection is valid, if it's already been retrieved.
   */
  private static boolean bConnectionValid = false;
  
  /**
   * Properties object for holding the connection information.
   */
  private static Properties props = new Properties();
  
  /**
   * The properties file name.
   */
  private static final String PROPS_FILE_NAME = "dbinfo.prop";
  
  
  /**
   * Default constructor.  Make it private since this class
   * does not need to be instantiated.
   */
  private Database()
  {
    super();
  }
  
  
  /**
   * Returns the default database connection.
   * 
   * @return the result of the operation
   */
  protected static boolean loadConnection()
  {
    // Check whether we've already tried to load the connection
    if (bRetrievedConnection)
    {
      // Check for an invalid connection
      if ((conn == null) || (!bConnectionValid))
      {
        // There's an error, so set the error flag
        bRetrievedConnection = false;
        return false;
      }
      
      // Connection is already loaded and valid
      return true;
    }
    
    // We have not yet tried to get a connection, so
    // record that we are trying now
    bRetrievedConnection = true;
    
    // Instantiate the properties object
    try
    {
      props.load(new FileInputStream(new File(PROPS_FILE_NAME)));
    }
    catch (FileNotFoundException e)
    {
      System.err.println(e.getMessage());
    }
    catch (IOException e)
    {
      System.err.println(e.getMessage());
    }
    
    // Read the properties needed to make a database connection
    final String dbHost = props.getProperty("db.server", "127.0.0.1");
    final String dbName = props.getProperty("db.name", "dbname");
    final String dbPort = props.getProperty("db.port", "3306");
    final String dbUser = props.getProperty("db.user", "userid");
    final String dbPass = props.getProperty("db.pw", "password");
    
    // Build the connection string
    StringBuffer buf = new StringBuffer(120);
    buf.append("jdbc:mysql://").append(dbHost);
    
    // Check if the port is set
    if ((dbPort != null) && (dbPort.length() > 0))
    {
      buf.append(":").append(dbPort);
    }
    
    // Append the remainder of the connection URL (except password)
    buf.append("/").append(dbName).append("?user=").append(dbUser)
       .append("&password=");
    
    // Make a copy of the buffer, without the password.  We
    // add a string of 8 *'s instead.  This is displayed to
    // the user in case of an error getting a connection.
    StringBuffer bufNoPassword = new StringBuffer(buf.toString());
    bufNoPassword.append("********");
    
    // Now append the password
    buf.append(dbPass);
    
    // Debugging
    // LexiconTool.debug("Connection: " + buf.toString());
    
    // Load the driver class
    if (!(loadDriver()))
    {
      // An error occurred
      return false;
    }
    
    // Get the connection
    if (!getConnection(buf.toString()))
    {
      // An error occurred
      return false;
    }
    
    // Check the connection
    if (conn == null)
    {
      // An error occurred; overwrite the error message/code
      System.err.println("Unable to connect to the database server via " +
          bufNoPassword.toString());
      return false;
    }
    
    // Return the result
    return true;
  }
  
  
  /**
   * Load the MySQL JDBC driver.
   * 
   * @return the result of the operation
   */
  protected static boolean loadDriver()
  {
    // Set the default return value
    boolean bResult = false;
    
    // Try to load the driver
    try
    {
      Class.forName("com.mysql.jdbc.Driver").newInstance();
      
      // If we reach this point, it was successful
      bResult = true;
    }
    catch (ClassNotFoundException cnfe)
    {
      System.err.println(cnfe.getMessage());
    }
    catch (InstantiationException ie)
    {
      System.err.println(ie.getMessage());
    }
    catch (IllegalAccessException iae)
    {
      System.err.println(iae.getMessage());
    }
    
    // Return the result of the operation
    return bResult;
  }
  
  
  /**
   * Gets a Connection object from the database server.
   * The connection is not in auto-commit mode, so
   * transactions need to be either commited or rolled
   * back. Returns null on error.
   * 
   * @param connUrl string used to get a connection
   * @return the result of the operation
   */
  protected static boolean getConnection(final String connUrl)
  {
    // The result of the operation
    boolean bResult = false;
    
    try
    {
      conn = DriverManager.getConnection(connUrl);
      if (conn != null)
      {
        // Mark the connection as not autocommit
        conn.setAutoCommit(false);
        
        // Mark the connection as valid
        bConnectionValid = true;
        
        // The operation was successful
        bResult = true;
      }
    }
    catch (SQLException sqlex)
    {
      // Null out the connection object
      conn = null;
    }
    
    // Return the connection object
    return bResult;
  }
  
  
  /**
   * Executes a database SELECT.
   * 
   * @param sqlCmd the database ststement to execute
   * @param listData will hold the retrieved data
   * @param fetcher used to retrieve the selected database columns
   * @return the result of the operation
   */
  protected static boolean executeSelect(final String sqlCmd,
                                         final List listData,
                                         final FetchDatabaseRecords fetcher)
  {
    return executeSelect(sqlCmd, listData, fetcher, null);
  }
  
  
  /**
   * Executes a database SELECT.
   * 
   * @param sqlCmd the database ststement to execute
   * @param listData will hold the retrieved data
   * @param fetcher used to retrieve the selected database columns
   * @param arrayStrings the array of strings to supply to the query
   * @return the result of the operation
   */
  protected static boolean executeSelect(final String sqlCmd,
                                         final List listData,
                                         final FetchDatabaseRecords fetcher,
                                         final String[] arrayStrings)
  {
    // The return value
    boolean bResult = false;
    
    // Check the SQL command
    if ((sqlCmd == null) || (sqlCmd.length() < 1))
    {
      return bResult;
    }
    
    // Check the list parameter
    if (listData == null)
    {
      return bResult;
    }
    
    // Check the fetcher
    if (fetcher == null)
    {
      return bResult;
    }
    
    // Get the connection object and check for an error
    bResult = loadConnection();
    if (conn == null)
    {
      return bResult;
    }
    
    // Execute the statement and get the returned ID
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try
    {
      // Create the Statement object from the connection
      stmt = conn.prepareStatement(sqlCmd);
      if (null != stmt)
      {
        // Set any strings in the SQL command
        if ((arrayStrings != null) && (arrayStrings.length > 0))
        {
          final int nSize = arrayStrings.length;
          for (int nIndex = 0; nIndex < nSize; ++nIndex)
          {
            stmt.setString(nIndex + 1, arrayStrings[nIndex]);
          }
        }
        
        // Now execute the query and save the result set
        rs = stmt.executeQuery();
        
        // If we reached this point, no error was found
        bResult = true;
        
        // Check for a result
        if (rs != null)
        {
          // Get the data from the result set
          fetcher.getFields(rs, listData);
        }
      }
    }
    catch (SQLException sqlex)
    {
      System.err.println(sqlex.getMessage());
    }
    finally
    {
      // Close the ResultSet if it's not null
      try
      {
        if (rs != null)
        {
          rs.close();
          rs = null;
        }
      }
      catch (SQLException sqle)
      {
        System.err.println(sqle.getMessage());
      }
      
      // Close the Statement if it's not null
      try
      {
        if (stmt != null)
        {
          stmt.close();
          stmt = null;
        }
      }
      catch (SQLException sqle)
      {
        System.err.println(sqle.getMessage());
      }
    }
    
    // Return the result of the operation
    return bResult;
  }
}
