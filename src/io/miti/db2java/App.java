package io.miti.db2java;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Standalone application to generate Java classes for the
 * tables in a database.  The columns in the table will
 * cause the corresponding class to have member variables
 * of the appropriate type.  The class will have a default
 * constructor and a getter/setter for each member variable.
 * 
 * @author mwallace
 * @version 1.0
 */
public final class App
{
  /**
   * Default property file name.
   */
  private static final String DEFAULT_PROP_FILE = "db2java.prop";
  
  /**
   * The name of the properties file.
   */
  private String propFilename = null;
  
  /**
   * The line separator for this system.
   */
  private String lineSep = null;
  
  /**
   * Properties read from the properties file.
   */
  private Properties props = null;
  
  
  /**
   * Default constructor.
   */
  private App()
  {
    super();
  }
  
  
  /**
   * Default constructor.
   * 
   * @param propName the name of the properties file
   */
  public App(final String propName)
  {
    super();
    
    // Save the properties filename
    propFilename = propName;
    
    // Get the line separator
    getLineSeparator();
  }
  
  
  /**
   * Get the line separator for this OS.
   */
  private void getLineSeparator()
  {
    lineSep = System.getProperty("line.separator");
  }
  
  
  /**
   * Get the connection.
   * 
   * @return the database connection
   */
  private Connection getConnection()
  {
    // Get the JDBC URL
    String url = getProperty("jdbc.url");
    
    // Get the connection
    Connection conn = null;
    try
    {
      conn = DriverManager.getConnection(url);
    }
    catch (SQLException e)
    {
      writeErr("Error: The connection could not be made");
    }
    
    // Return the connection
    return conn;
  }
  
  
  /**
   * Write the error message.
   * 
   * @param msg the error message
   */
  private static void writeErr(final String msg)
  {
    System.err.println(msg);
  }
  
  
  /**
   * Load the MySQL JDBC driver.
   * 
   * @return the result of the operation
   */
  private boolean loadDriver()
  {
    // Set the default return value
    boolean bResult = false;
    
    // Get the class name
    final String className = getProperty("jdbc.driver");
    if ((className == null) || (className.length() < 1))
    {
      // Nothing to do
      return true;
    }
    
    // Try to load the driver
    try
    {
      Class.forName(className);
      
      // If we reach this point, it was successful
      bResult = true;
    }
    catch (ClassNotFoundException cnfe)
    {
      writeErr("Exception loading driver: " + cnfe.getMessage());
    }
    catch (Exception ie)
    {
      writeErr("Exception loading driver: " + ie.getMessage());
    }
    
    // Return the result of the operation
    return bResult;
  }
  
  
  /**
   * Read the properties file.
   */
  private void readProperties()
  {
    // Instantiate the properties object
    props = new Properties();
    
    // Load the properties from the file
    try
    {
      props.load(new FileInputStream(new File(propFilename)));
    }
    catch (FileNotFoundException e)
    {
      props = null;
    }
    catch (IOException e)
    {
      props = null;
    }
  }
  
  
  /**
   * Process the properties file and create the
   * output script.
   */
  private void process()
  {
    // Read the app properties
    readProperties();
    
    // Set up the database and get the connection
    Connection conn = loadDatabaseInfo();
    if (conn == null)
    {
      return;
    }
    
    // Get a list of table names, and the column info data for
    // each table
    Map<String, List<TableColumn>> dbInfo = getTableData(conn);
    
    // Close the connection
    closeConnection(conn);
    conn = null;
    
    // Check the table data
    if (dbInfo == null)
    {
      return;
    }
    
    // Build the output files from the table data
    buildOutputFiles(dbInfo);
  }
  
  
  /**
   * Build the output files.
   * 
   * @param dbInfo the database information (tables and columns)
   */
  private void buildOutputFiles(final Map<String, List<TableColumn>> dbInfo)
  {
    // Get the output directory
    final String outDir = getProperty("output.dir");
    final String outPackage = getProperty("output.package");
    final boolean writeFetch = getPropertyAsBoolean("write.fetch");
    
    // Make sure the output directory exists
    File outputDir = new File(outDir);
    boolean rc = createOutputDir(outputDir);
    if (!rc)
    {
      writeErr("Error creating the output directory " + outDir);
    }
    
    // Iterate over the list of tables
    Set<String> keys = dbInfo.keySet();
    Iterator tables = keys.iterator();
    while (tables.hasNext())
    {
      // Get the next table name
      final String table = (String) tables.next();
      
      // Get the column info for this table
      List<TableColumn> cols = dbInfo.get(table);
      
      // Generate the class name from the table name
      final String className =
        Utility.toTitleCaseWithSplit(table, '_', true, true);
      
      // Write out the Java file for this table
      generateJavaClass(outputDir, outPackage, table,
                        cols, className, writeFetch);
    }
  }
  
  
  /**
   * Create the output directory.
   * 
   * @param outputDir the output directory
   * @return whether it was successfully created
   */
  private static boolean createOutputDir(final File outputDir)
  {
    // Check the File object to see if it already exists
    if (outputDir.exists())
    {
      // Return true if this is a directory; otherwise, return
      // false (it's a file)
      return (outputDir.isDirectory());
    }
    
    // Make the directory and return the success
    return (outputDir.mkdirs());
  }
  
  
  /**
   * Generate the Java class for the table.
   * 
   * @param outputDir the output directory
   * @param packageName the package name of the class
   * @param tableName the table name
   * @param cols the list of columns and types
   * @param className the name of the Java class
   * @param writeFetch whether to include the Fetch info in the Java class
   */
  private void generateJavaClass(final File outputDir,
                                 final String packageName,
                                 final String tableName,
                                 final List<TableColumn> cols,
                                 final String className,
                                 final boolean writeFetch)
  {
    // The output file
    File file = new File(outputDir, className + ".java");
    
    // Write to the file
    BufferedWriter out = null;
    try
    {
      // Open the output writer
      out = new BufferedWriter(new FileWriter(file));
      
      // Build the date
      SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
      String dateStr = sdf.format(new Date());
      
      // Write the header comment
      out.write("/*" + lineSep);
      out.write(" * Java class for the " + tableName + " database table." + lineSep);
      out.write(" * Generated on " + dateStr + " by DB2Java." + lineSep);
      out.write(" */" + lineSep + lineSep);
      
      // Write the package name (if any)
      if ((packageName != null) && (packageName.length() > 0))
      {
        out.write("package " + packageName + ";" + lineSep + lineSep);
      }
      
      // Write out the required imports for fetching
      if (writeFetch)
      {
        out.write("import java.sql.ResultSet;" + lineSep);
        out.write("import java.util.ArrayList;" + lineSep);
        out.write("import java.util.List;" + lineSep + lineSep);
      }
      
      // Write the class comment
      out.write("/**" + lineSep);
      out.write(" * Java class to encapsulate the " + tableName + " table." + lineSep);
      out.write(" *" + lineSep);
      out.write(" * @version 1.0" + lineSep);
      out.write(" */" + lineSep);
      
      // Write the class declaration
      out.write("public final class " + className);
      if (writeFetch)
      {
        out.write(" implements FetchDatabaseRecords");
      }
      out.write(lineSep + "{" + lineSep);
      
      // Write the field declarations
      for (TableColumn col : cols)
      {
        out.write("  /**" + lineSep);
        out.write("   * The table column " + col.getColName() + "." + lineSep);
        out.write("   */" + lineSep);
        out.write("  private " + col.getTypeAsJavaClass() + " " +
                  col.getFieldName() + ";" + lineSep + "  " + lineSep);
      }
      
      // Write the default constructor
      out.write("  " + lineSep);
      out.write("  /**" + lineSep);
      out.write("   * Default constructor." + lineSep);
      out.write("   */" + lineSep);
      out.write("  public " + className + "()" + lineSep);
      out.write("  {" + lineSep);
      out.write("    super();" + lineSep);
      out.write("  }" + lineSep);
      
      // See if we're including fetch information
      if (writeFetch)
      {
        writeFetchMethods(tableName, cols, className, out);
      }
      
      // Write the getters/setters
      for (TableColumn col : cols)
      {
        // The field name with the first character in uppercase
        final String fieldInUC = Utility.setFirstCharacter(col.getFieldName(), true);
        
        // Write the getter
        out.write("  " + lineSep);
        out.write("  " + lineSep);
        out.write("  /**" + lineSep);
        out.write("   * Get the value for " + col.getFieldName() + "." + lineSep);
        out.write("   *" + lineSep);
        out.write("   * @return the " + col.getFieldName() + lineSep);
        out.write("   */" + lineSep);
        out.write("  public " + col.getTypeAsJavaClass() + " get" +
                  fieldInUC + "()" + lineSep);
        out.write("  {" + lineSep);
        out.write("    return " + col.getFieldName() + ";" + lineSep);
        out.write("  }" + lineSep);
        
        // Write the setter
        out.write("  " + lineSep);
        out.write("  " + lineSep);
        out.write("  /**" + lineSep);
        out.write("   * Update the value for " + col.getFieldName() + "." + lineSep);
        out.write("   *" + lineSep);
        out.write("   * @param p" + fieldInUC + " the new value for " +
                  col.getFieldName() + lineSep);
        out.write("   */" + lineSep);
        out.write("  public void set" + fieldInUC + "(final " +
                  col.getTypeAsJavaClass() + " p" + fieldInUC + ")" + lineSep);
        out.write("  {" + lineSep);
        out.write("    " + col.getFieldName() + " = p" + fieldInUC + ";" + lineSep);
        out.write("  }" + lineSep);
      }
      
      // Write the class declaration
      out.write("}" + lineSep);
      
      // Close the writer
      out.close();
      out = null;
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    finally
    {
      if (out != null)
      {
        try
        {
          out.close();
        }
        catch (IOException e)
        {
          e.printStackTrace();
        }
        
        out = null;
      }
    }
  }
  
  
  /**
   * Write out the methods for fetching info from a table.
   * 
   * @param tableName the table name
   * @param cols the table columns
   * @param className the output class name
   * @param out the writer
   * @throws IOException exception from writing
   */
  private void writeFetchMethods(final String tableName,
                                 final List<TableColumn> cols,
                                 final String className,
                                 final BufferedWriter out)
     throws IOException
  {
    // Write out the method to get the columns from a result set
    out.write("  " + lineSep);
    out.write("  " + lineSep);
    out.write("  /**" + lineSep);
    out.write("   * Implement the FetchDatabaseRecords interface." + lineSep);
    out.write("   * " + lineSep);
    out.write("   * @param rs the result set to get the data from" + lineSep);
    out.write("   * @param listRecords the list of data to add to" + lineSep);
    out.write("   * @return the success of the operation" + lineSep);
    out.write("   */" + lineSep);
    out.write("  @SuppressWarnings(\"unchecked\")" + lineSep);
    out.write("  public boolean getFields(final ResultSet rs," + lineSep);
    out.write("                           final List listRecords)" + lineSep);
    out.write("  {" + lineSep);
    out.write("    // Default return value" + lineSep);
    out.write("    boolean bResult = false;" + lineSep);
    out.write("    " + lineSep);
    out.write("    // Set up our try/catch block" + lineSep);
    out.write("    try" + lineSep);
    out.write("    {" + lineSep);
    out.write("      // Iterate over the result set" + lineSep);
    out.write("      while (rs.next())" + lineSep);
    out.write("      {" + lineSep);
    out.write("        // Instantiate a new object" + lineSep);
    out.write("        " + className + " obj = new " + className + "();" + lineSep);
    out.write("        " + lineSep);
    out.write("        // Save the data in our object" + lineSep);
    
    // Iterate over the columns
    StringBuilder builder = new StringBuilder(100);
    for (int i = 0; i < cols.size(); ++i)
    {
      builder.setLength(0);
      builder.append("        obj.").append(cols.get(i).getFieldName())
             .append(" = rs.get").append(cols.get(i).getTypeAsAccessor()).append("(")
             .append(Integer.toString(i + 1)).append(");")
             .append(lineSep);
      out.write(builder.toString());
    }
    
    out.write("        " + lineSep);
    out.write("        // Add to our list" + lineSep);
    out.write("        listRecords.add(obj);" + lineSep);
    out.write("      }" + lineSep);
    out.write("      " + lineSep);
    out.write("      // There was no error" + lineSep);
    out.write("      bResult = true;" + lineSep);
    out.write("    }" + lineSep);
    out.write("    catch (java.sql.SQLException sqle)" + lineSep);
    out.write("    {" + lineSep);
    out.write("      // Add the exception to the master list and save the" + lineSep);
    out.write("      // result as the error code" + lineSep);
    out.write("      System.err.println(sqle.getMessage());" + lineSep);
    out.write("    }" + lineSep);
    out.write("    " + lineSep);
    out.write("    // Return the result of the operation" + lineSep);
    out.write("    return bResult;" + lineSep);
    out.write("  }" + lineSep);
    
    // Write the method to generate the SELECT statement
    out.write("  " + lineSep);
    out.write("  " + lineSep);
    out.write("  /**" + lineSep);
    out.write("   * Get all objects from the database." + lineSep);
    out.write("   * " + lineSep);
    out.write("   * @return a list of all objects in the database" + lineSep);
    out.write("   */" + lineSep);
    out.write("  public static List<" + className + "> getList()" + lineSep);
    out.write("  {" + lineSep);
    out.write("    // This will hold the list that gets returned" + lineSep);
    out.write("    List<" + className + "> listData = new ArrayList<" + className + ">(100);" + lineSep);
    out.write("    " + lineSep);
    out.write("    // Build our query" + lineSep);
    out.write("    StringBuffer buf = new StringBuffer(100);" + lineSep);
    
    // Write out the query (column names)
    StringBuilder sb = new StringBuilder(200);
    sb.append(cols.get(0).getColName());
    for (int i = 1; i < cols.size(); ++i)
    {
      sb.append(", ").append(cols.get(i).getColName());
    }
    out.write("    buf.append(\"select " + sb.toString() + " \")" + lineSep);
    
    out.write("       .append(\"from " + tableName + "\");" + lineSep);
    out.write("    " + lineSep);
    out.write("    // Get all of the objects from the database" + lineSep);
    out.write("    boolean bResult = Database.executeSelect(buf.toString(), listData, new ");
    out.write(className + "());" + lineSep);
    out.write("    if (!bResult)" + lineSep);
    out.write("    {" + lineSep);
    out.write("      // An error occurred" + lineSep);
    out.write("      listData.clear();" + lineSep);
    out.write("      listData = null;" + lineSep);
    out.write("    }" + lineSep);
    out.write("    " + lineSep);
    out.write("    // Return the list" + lineSep);
    out.write("    return listData;" + lineSep);
    out.write("  }" + lineSep);
  }
  
  
  /**
   * Get the table information.
   * 
   * @param conn the database connection
   * @return the map of table name and corresponding column info
   */
  private Map<String, List<TableColumn>> getTableData(final Connection conn)
  {
    // Get the table names
    List<String> tables = getTableNames(conn);
    if ((tables == null) && (tables.size() < 1))
    {
      return null;
    }
    
    // Declare the map that gets returned
    Map<String, List<TableColumn>> dbInfo =
      new HashMap<String, List<TableColumn>>(tables.size());
    
    // Iterate over the table names
    for (String table : tables)
    {
      // Get the list of column names for each table
      List<TableColumn> cols = getColumns(table, conn);
      
      // Add the table name and column data to the map
      dbInfo.put(table, cols);
    }
    
    // Return the map
    return dbInfo;
  }


  /**
   * Return a database connection.
   * 
   * @return a database connection
   */
  private Connection loadDatabaseInfo()
  {
    // Load the JDBC driver
    if (!loadDriver())
    {
      return null;
    }
    
    // Get the connection
    Connection conn = getConnection();
    return conn;
  }
  
  
  /**
   * Returns information about the columns in the table.
   * 
   * @param table the table name
   * @param conn the connection
   * @return the list of table info
   */
  private List<TableColumn> getColumns(final String table,
                                       final Connection conn)
  {
    // This is the object that gets returned
    List<TableColumn> listColumns = new ArrayList<TableColumn>(10);
    
    // Get the info for all columns in this table
    try
    {
      // Get the database metadata
      DatabaseMetaData dbmd = conn.getMetaData();
      
      // Get the table info
      ResultSet rs = dbmd.getColumns(null, null, table.toUpperCase(), null);
      
      // Iterate over all column info for the table
      while (rs.next())
      {
        // Save the column info
        final String colName = rs.getString("COLUMN_NAME");
        final int colType =
          TableColumn.getJavaTypeForDBType(rs.getInt("DATA_TYPE"),
                                           rs.getString("TYPE_NAME"));
        
        // Normalize the column name, save as the field name
        final String fieldName = generateFieldFromColumn(colName);
        
        // Construct a TableColumn object
        TableColumn colInfo = new TableColumn(colName, colType, fieldName);
        
        // Add it to our list
        listColumns.add(colInfo);
      }
      
      // Close the result set
      rs.close();
      rs = null;
    }
    catch (SQLException e)
    {
      writeErr("Exception getting columns: " + e.getMessage());
    }
    
    // Return the column info
    return listColumns;
  }
  
  
  /**
   * Generate the class variable name from the column name.
   * 
   * @param columnName the column name
   * @return the corresponding class variable name
   */
  private static String generateFieldFromColumn(final String columnName)
  {
    // Check for upper-case runs in the column name
    final String colName = checkForUC(columnName);
    
    // Save the length
    final int colLen = colName.length();
    
    // This is the string that gets returned
    StringBuilder sb = new StringBuilder(colLen);
    
    // Iterate over the characters in the string
    boolean makeLower = true;
    for (int i = 0; i < colLen; ++i)
    {
      // Save the current character
      final char ch = colName.charAt(i);
      
      // If it's not a letter or a digit, skip it
      if (!Character.isLetterOrDigit(ch))
      {
        // Make the next character uppercase if
        // the string builder already has some
        // characters
        makeLower = (sb.length() == 0);
        
        // Go to the next character
        continue;
      }
      
      // Is this character uppercase?
      boolean isLower = Character.isLowerCase(ch);
      if (!isLower && (sb.length() > 0))
      {
        // It is, and the output string is not empty,
        // so make the character uppercase
        makeLower = false;
      }
      
      // Add the character to the string, after modifying the case
      if (makeLower)
      {
        sb.append(Character.toLowerCase(ch));
      }
      else
      {
        sb.append(Character.toUpperCase(ch));
      }
      
      // By default, the next character will be made lowercase
      makeLower = true;
    }
    
    // Return the string
    return sb.toString();
  }
  
  
  /**
   * Check for a run of uppercase characters.
   * 
   * @param inputName the string to check
   * @return modified input string
   */
  private static String checkForUC(final String inputName)
  {
    // See if the string has a non-uppercase letter
    boolean bHasNonUC = false;
    final int nLen = inputName.length();
    for (int i = 0; i < nLen; ++i)
    {
      // Check the character
      if (!Character.isUpperCase(inputName.charAt(i)))
      {
        // We found a character that's either not a letter,
        // or it's lowercase, so save the state and break
        bHasNonUC = true;
        break;
      }
    }
    
    // Check if a non-UC letter was found
    if (!bHasNonUC)
    {
      // None were found, so just return the string in all lower-case
      return (inputName.toLowerCase());
    }
    
    // Go through the string, looking for runs of uppercase letters
    StringBuilder sb = new StringBuilder(inputName);
    for (int i = 0; (i < nLen); ++i)
    {
      // Find the next uppercase character
      final char ch = sb.charAt(i);
      if (Character.isUpperCase(ch))
      {
        // Find the end of the run
        int j = i + i;
        boolean bContinue = true;
        while (bContinue && (j < nLen))
        {
          if (!Character.isUpperCase(sb.charAt(j)))
          {
            bContinue = false;
          }
          else
          {
            ++j;
          }
        }
        
        if (!bContinue)
        {
          if (Character.isLowerCase(sb.charAt(j)))
          {
            putInLowerCase(sb, i + 1, j - 1);
          }
          else
          {
            putInLowerCase(sb, i + 1, j);
          }
        }
        else
        {
          // Hit the end of the line
          putInLowerCase(sb, i + 1, j);
        }
        
        i = j;
      }
    }
    
    // Return the string
    return sb.toString();
  }
  
  
  /**
   * Put the input string in lowercase.
   * 
   * @param str the input string
   * @param nStart the start of the run
   * @param nEnd the end (exclusive) of the run
   */
  private static void putInLowerCase(final StringBuilder str,
                                     final int nStart,
                                     final int nEnd)
  {
    final int nLen = str.length();
    final int end = Math.min(nEnd, nLen);
    for (int i = nStart; i < end; ++i)
    {
      str.setCharAt(i, Character.toLowerCase(str.charAt(i)));
    }
  }


  /**
   * Return the list of table names.
   * 
   * @param conn database connection
   * @return the list of table names
   */
  private List<String> getTableNames(final Connection conn)
  {
    // This will hold the list of table names
    List<String> tableNames = new ArrayList<String>(20);
    
    // Get the list of table names
    try
    {
      // Gets the database metadata
      DatabaseMetaData dbmd = conn.getMetaData();
      
      // Specify the type of object; in this case we want tables
      String[] types = {"TABLE"};
      ResultSet resultSet = dbmd.getTables(null, null, "%", types);
      
      // Get the table names
      while (resultSet.next())
      {
        // Get the table name
        String tableName = resultSet.getString(3);
        
        // Save the table name
        tableNames.add(tableName);
      }
      
      // Close the result set
      resultSet.close();
      resultSet = null;
    }
    catch (SQLException e)
    {
      writeErr("Exception getting the table names");
    }
    
    return tableNames;
  }
  
  
  /**
   * Close the database connection.
   *
   * @param conn the database connection
   */
  private void closeConnection(final Connection conn)
  {
    // Check if it's null
    if (conn == null)
    {
      return;
    }
    
    // Close it
    try
    {
      conn.close();
    }
    catch (SQLException e)
    {
      writeErr("SQL Closing Exception: " + e.getMessage());
    }
  }
  
  
  /**
   * Get the specified property value.
   * 
   * @param property the property name
   * @return the value for the property
   */
  private String getProperty(final String property)
  {
    // Check the properties object.
    if (props == null)
    {
      return "";
    }
    
    // Get the value for this property
    String sValue = props.getProperty(property);
    return ((sValue == null) ? "" : sValue);
  }
  
  
  /**
   * Get the specified property value, as a boolean.
   * 
   * @param property the property name
   * @return the value for the property
   */
  private boolean getPropertyAsBoolean(final String property)
  {
    // Check the properties object.
    if (props == null)
    {
      return false;
    }
    
    // Get the value for this property
    String sValue = props.getProperty(property);
    if (sValue == null)
    {
      return false;
    }
    
    return (sValue.equals("1"));
  }
  
  
  /**
   * Get the properties input file name.
   * 
   * @param args arguments passed to the program
   * @param option the optioin string to search for
   * @param defaultValue the default value to return if not found
   * @return the properties input file name
   */
  private static String getArgument(final String[] args,
                                    final String option,
                                    final String defaultValue)
  {
    // The property file name
    String name = defaultValue;
    
    // Check the arguments
    if ((args == null) || (args.length < 1))
    {
      // No arguments
      return name;
    }
    
    // Iterate over the list and look for a match on the option
    // and grab everything after it as the value
    boolean bFound = false;
    final int nSize = args.length;
    for (int i = 0; (!bFound) && (i < nSize); ++i)
    {
      // Check if this argument is the option we want
      String arg = args[i];
      if ((arg.startsWith(option)) && (arg.length() > option.length()))
      {
        bFound = true;
        name = arg.substring(option.length());
      }
    }
    
    // Return the file name
    return name;
  }
  
  
  /*
  private static void testColumnName(final String colName,
                                     final String expectedValue)
  {
    final String fldName = App.generateFieldFromColumn(colName);
    if (!fldName.equals(expectedValue))
    {
      throw new RuntimeException("The field name for " + colName + " is " +
                                 fldName + " but should be " + expectedValue);
    }
  }
  
  
  private static void testField2Column()
  {
    System.out.println("Starting...");
    
    testColumnName("fldName", "fldName");
    testColumnName("Fld_Name", "fldName");
    testColumnName("Std_dev", "stdDev");
    testColumnName("_FldName", "fldName");
    testColumnName("FieldName", "fieldName");
    testColumnName("Field_name", "fieldName");
    testColumnName("FLD", "fld");
    testColumnName("FL", "fl");
    testColumnName("NDB_No", "ndbNo");
    testColumnName("FLD_Num", "fldNum");
    testColumnName("FLDNum_Can", "fldNumCan");
    testColumnName("FLDNum", "fldNum");
    testColumnName("FLee", "fLee");
    testColumnName("FLDe", "flDe");
    testColumnName("FLD_FL", "fldFl");
    
    System.out.println("Finished.  No errors.");
  }
  */
  
  
  /**
   * Entry point for the application.
   * 
   * @param args arguments to the application
   */
  public static void main(final String[] args)
  {
    String propName = getArgument(args, "-prop=", DEFAULT_PROP_FILE);
    new App(propName).process();
  }
}
