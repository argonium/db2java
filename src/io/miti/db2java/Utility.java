package io.miti.db2java;

import java.util.StringTokenizer;

/**
 * @author mwallace
 * @version 1.0
 */
public final class Utility
{
  /**
   * Default constructor.
   */
  private Utility()
  {
    super();
  }
  
  
  /**
   * Split a string, and optionally put it in title case.
   * 
   * @param str the string to parse
   * @param split the character to split the string on
   * @param toTitleCase whether to put it in title case
   * @param restToLower if going to title case, whether to put
   *        characters after the first one in lowercase
   * @return the converted string
   */
  public static String toTitleCaseWithSplit(final String str,
                                            final char split,
                                            final boolean toTitleCase,
                                            final boolean restToLower)
  {
    // Split the input string
    StringTokenizer st = new StringTokenizer(str, Character.toString(split));
    
    // The string that gets returned
    StringBuilder sb = new StringBuilder(str.length());
    
    // Iterate over the strings
    while (st.hasMoreTokens())
    {
      // Check the processing
      if (toTitleCase)
      {
        // Append the current string in title case
        sb.append(toTitleCase(st.nextToken(), restToLower));
      }
      else
      {
        sb.append(st.nextToken());
      }
    }
    
    // Return the built string
    return (sb.toString());
  }
  
  
  /**
   * Convert a string to title case, with the first term
   * in lower case.
   * 
   * @param str the input string
   * @param putRestInLC whether to put the rest of the string in lowercase
   * @return the modified string
   */
  public static String toFirstLowerRestTitle(final String str,
                                             final boolean putRestInLC)
  {
    // First get the whole string in title case
    String title = toTitleCase(str, putRestInLC);
    
    // Save the length
    final int len = title.length();
    if (len < 1)
    {
      return title;
    }
    else if (len == 1)
    {
      // The string has a length of 1, so just return
      // it in lowercase
      return (title.toLowerCase());
    }
    else if (Character.isLowerCase(title.charAt(0)))
    {
      // The first character is already lower case
      return title;
    }
    
    // Return the converted string
    return (Character.toLowerCase(title.charAt(0)) + title.substring(1));
  }
  
  
  /**
   * Convert a string to title case.
   *
   * @param inStr string to put in title case
   * @param putRestInLC whether the rest of the string
   *                    should be made lowercase
   * @return the input string in title case
   */
  public static String toTitleCase(final String inStr,
                                   final boolean putRestInLC)
  {
    // Check for a null or empty string
    if ((inStr == null) || (inStr.length() < 1))
    {
      return "";
    }
    else
    {
      // Save the length
      final int nLen = inStr.length();
      
      // If one character, make it uppercase and return it
      if (nLen == 1)
      {
        return inStr.toUpperCase();
      }
      
      // Set this to true because we want to make the first character uppercase
      boolean blankFound = true;
      
      // Save the string to a stringbuffer
      StringBuffer buf = new StringBuffer(inStr);
      
      // Traverse the character array
      for (int nIndex = 0; nIndex < nLen; ++nIndex)
      {
        // Save the current character
        final char ch = buf.charAt(nIndex);
        
        // If we hit a space, set a flag so we make the next non-space char uppercase
        if (ch == ' ')
        {
          blankFound = true;
          continue;
        }
        else
        {
          // See if the previous character was a space
          if (blankFound)
          {
            // Check if this is lowercase
            if (Character.isLowerCase(ch))
            {
              // Make the character uppercase
              buf.setCharAt(nIndex, Character.toUpperCase(ch));
            }
          }
          else
          {
            // Check if the rest the string should be made lowercase
            if (putRestInLC)
            {
              // Check if this is uppercase
              if (Character.isUpperCase(ch))
              {
                // Make the character lowercase
                buf.setCharAt(nIndex, Character.toLowerCase(ch));
              }
            }
          }
          
          // Clear the flag
          blankFound = false;
        }
      }
      
      // Return it
      return (buf.toString());
    }
  }
  
  
  /**
   * Set the first character in the string to either
   * lowercase or uppercase.
   * 
   * @param str the string to convert
   * @param toUpper whether to make the first character uppercase or lowercase
   * @return the converted string
   */
  public static String setFirstCharacter(final String str,
                                         final boolean toUpper)
  {
    // Check the input
    if ((str == null) || (str.length() < 1))
    {
      return "";
    }
    
    // Build the string builder
    StringBuilder sb = new StringBuilder(str);
    
    // Check the operation to perform
    char ch = (toUpper ? Character.toUpperCase(str.charAt(0))
              : Character.toLowerCase(str.charAt(0)));
    
    // Update the character
    sb.setCharAt(0, ch);
    
    // Return the string
    return (sb.toString());
  }
}
