# db2java
DB2Java is a standalone Java application that makes it easy to generate Java files (class definitions) based on the tables in a database you specify. For each table, DB2Java will generate a .java file, with the class name the same as the table name. Each column in the table will have a corresponding member variable in the .java file.

To run the program, Java 5 or later is required. Use this command to execute the application:

```
java -jar db2java.jar
```

The program needs a file called db2java.prop in the same directory. This properties file has the following fields:

```
jdbc.url=<JDBC URL>: The URL to the JDBC database
jdbc.driver=<JDBC Driver Class>: The name of the JDBC driver class (e.g., com.mysql.jdbc.Driver)
output.dir=<Output file directory>: The directory that the generated .java files are placed in
output.package=<Package name>: The package that the generated Java files are in
```
Below is a sample db2java.prop properties file:

```
jdbc.url=jdbc:mysql://192.168.1.15:3306/somedb?user=root&password=pass
jdbc.driver=com.mysql.jdbc.Driver
output.dir=out
output.package=com.nexagis.db2java
```

This properties file will cause the program to first load the class specified in the jdbc.driver statement, and then connect to the database specified in the jdbc.url statement. The generated Java files will be placed in the output.dir directory, and the classes belonging to the output.package package.

Each .java file will include all columns from the table, with a default constructor and getters and setters for all member variables.

This source code is released under the MIT license.
