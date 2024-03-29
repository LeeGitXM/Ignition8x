/**
 *   (c) 2013-2015  ILS Automation. All rights reserved. 
 */
package com.ils.blt.common.block;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)

/**
 * This enumeration class describes the types of bindings available for a property.
 * For the option binding type, the available choices are a comma-delimited string
 * in the binding. The list is not user-editable.
 */
public enum BindingType
{
            NONE,
            ENGINE,
            OPTION,   // I only see this used in the noise generator, but appears as an option in the list
//            SCRIPT, // I don't see this used anywhere, so commented out.
            TAG_READ,
            TAG_READWRITE,
            TAG_WRITE,
            TAG_MONITOR
            ;
   
 /**
  * @return  a comma-separated list of all binding types in a single String.
  */
  public static String names()
  {
    StringBuffer names = new StringBuffer();
    for (BindingType type : BindingType.values())
    {
      names.append(type.name()+", ");
    }
    return names.substring(0, names.length()-2);
  }
}
