/*
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.truffleruby.core.regexp;

import org.jcodings.Encoding;
import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.EUCJPEncoding;
import org.jcodings.specific.UTF8Encoding;
import org.jcodings.specific.Windows_31JEncoding;
import org.truffleruby.core.string.KCode;
import org.truffleruby.parser.ReOptions;

public class RegexpOptions implements Cloneable {

    public static final RegexpOptions NULL_OPTIONS = new RegexpOptions(KCode.NONE, true);

    public RegexpOptions() {
        this(KCode.NONE, true);
    }

    public RegexpOptions(KCode kcode, boolean isKCodeDefault) {
        this.kcode = kcode;
        this.kcodeDefault = isKCodeDefault;

        assert kcode != null : "kcode must always be set to something";
    }

    public boolean isExtended() {
        return extended;
    }

    public void setExtended(boolean extended) {
        this.extended = extended;
    }

    public boolean isIgnorecase() {
        return ignorecase;
    }

    public void setIgnorecase(boolean ignorecase) {
        this.ignorecase = ignorecase;
    }

    public boolean isFixed() {
        return fixed;
    }

    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    public KCode getKCode() {
        return kcode;
    }

    /**
     * This regexp has an explicit encoding flag or 'nesu' letter associated with it.
     * 
     * @param kcode to be set
     */
    public void setExplicitKCode(KCode kcode) {
        this.kcode = kcode;
        kcodeDefault = false;
    }

    private KCode getExplicitKCode() {
        if (kcodeDefault == true) {
            return null;
        }

        return kcode;
    }

    /**
     * Whether the kcode associated with this regexp is implicit (aka default) or is specified explicitly (via 'nesu'
     * syntax postscript or flags to Regexp.new.
     */
    public boolean isKcodeDefault() {
        return kcodeDefault;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
    }

    public boolean isOnce() {
        return once;
    }

    public void setOnce(boolean once) {
        this.once = once;
    }

    public boolean isJava() {
        return java;
    }

    public void setJava(boolean java) {
        this.java = java;
    }

    public boolean isEncodingNone() {
        return encodingNone;
    }

    public void setEncodingNone(boolean encodingNone) {
        this.encodingNone = encodingNone;
    }

    public boolean isLiteral() {
        return literal;
    }

    public void setLiteral(boolean literal) {
        this.literal = literal;
    }

    public boolean isEmbeddable() {
        return multiline && ignorecase && extended;
    }

    public Encoding setup() {
        KCode explicitKCode = getExplicitKCode();

        // None will not set fixed
        if (explicitKCode == KCode.NONE) {
            setEncodingNone(true);
            return ASCIIEncoding.INSTANCE;
        }

        if (explicitKCode == KCode.EUC) {
            fixed = true;
            return EUCJPEncoding.INSTANCE;
        } else if (explicitKCode == KCode.SJIS) {
            fixed = true;
            return Windows_31JEncoding.INSTANCE;
        } else if (explicitKCode == KCode.UTF8) {
            fixed = true;
            return UTF8Encoding.INSTANCE;
        }

        return null;
    }

    /**
     * This int value is meant to only be used when dealing directly with the joni regular expression library. It
     * differs from embeddedOptions in that it only contains bit values which Joni cares about.
     */
    public int toJoniOptions() {
        int options = 0;
        // Note: once is not an option that is pertinent to Joni so we exclude it.
        if (multiline) {
            options |= ReOptions.RE_OPTION_MULTILINE;
        }
        if (ignorecase) {
            options |= ReOptions.RE_OPTION_IGNORECASE;
        }
        if (extended) {
            options |= ReOptions.RE_OPTION_EXTENDED;
        }
        return options;
    }

    /**
     * This int value is used by Regex#options
     */
    public int toOptions() {
        int options = 0;
        if (multiline) {
            options |= ReOptions.RE_OPTION_MULTILINE;
        }
        if (ignorecase) {
            options |= ReOptions.RE_OPTION_IGNORECASE;
        }
        if (extended) {
            options |= ReOptions.RE_OPTION_EXTENDED;
        }
        if (fixed) {
            options |= ReOptions.RE_FIXED;
        }
        if (encodingNone) {
            options |= ReOptions.RE_NONE;
        }
        return options;
    }

    public static RegexpOptions fromEmbeddedOptions(int embeddedOptions) {
        RegexpOptions options = fromJoniOptions(embeddedOptions);

        options.kcodeDefault = (embeddedOptions & ReOptions.RE_DEFAULT) != 0;
        options.literal = (embeddedOptions & ReOptions.RE_LITERAL) != 0;
        options.encodingNone = (embeddedOptions & ReOptions.RE_NONE) != 0;

        return options;
    }

    public static RegexpOptions fromJoniOptions(int joniOptions) {
        RegexpOptions options = new RegexpOptions();
        options.multiline = (joniOptions & ReOptions.RE_OPTION_MULTILINE) != 0;
        options.ignorecase = (joniOptions & ReOptions.RE_OPTION_IGNORECASE) != 0;
        options.extended = (joniOptions & ReOptions.RE_OPTION_EXTENDED) != 0;
        options.fixed = (joniOptions & ReOptions.RE_FIXED) != 0;
        options.once = (joniOptions & ReOptions.RE_OPTION_ONCE) != 0;

        return options;
    }

    public RegexpOptions withoutOnce() {
        RegexpOptions options = (RegexpOptions) clone();
        options.once = false;
        return options;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 11 * hash + (this.kcode != null ? this.kcode.hashCode() : 0);
        hash = 11 * hash + (this.fixed ? 1 : 0);
        hash = 11 * hash + (this.once ? 1 : 0);
        hash = 11 * hash + (this.extended ? 1 : 0);
        hash = 11 * hash + (this.multiline ? 1 : 0);
        hash = 11 * hash + (this.ignorecase ? 1 : 0);
        hash = 11 * hash + (this.java ? 1 : 0);
        hash = 11 * hash + (this.encodingNone ? 1 : 0);
        hash = 11 * hash + (this.kcodeDefault ? 1 : 0);
        hash = 11 * hash + (this.literal ? 1 : 0);
        return hash;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException cnse) {
            throw new RuntimeException(cnse);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RegexpOptions)) {
            return false;
        }

        // Note: literal and once can be different in this object but for the
        // sake of equality we ignore those two fields since those flags do
        // not affect Ruby equality.
        RegexpOptions o = (RegexpOptions) other;
        boolean equality = o.extended == extended &&
                o.fixed == fixed &&
                o.ignorecase == ignorecase &&
                o.java == java &&
                o.multiline == multiline;
        if (encodingNone || o.encodingNone) {
            return equality && o.kcode == kcode;
        } else {
            return equality &&
                    o.encodingNone == encodingNone &&
                    o.kcode == kcode &&
                    o.kcodeDefault == kcodeDefault;
        }
    }

    @Override
    public String toString() {
        return "RegexpOptions(kcode: " + kcode +
                (encodingNone == true ? ", encodingNone" : "") +
                (extended == true ? ", extended" : "") +
                (fixed == true ? ", fixed" : "") +
                (ignorecase == true ? ", ignorecase" : "") +
                (java == true ? ", java" : "") +
                (kcodeDefault == true ? ", kcodeDefault" : "") +
                (literal == true ? ", literal" : "") +
                (multiline == true ? ", multiline" : "") +
                (once == true ? ", once" : "") +
                ")";
    }

    private KCode kcode;
    private boolean fixed;
    private boolean once;
    private boolean extended;
    private boolean multiline;
    private boolean ignorecase;
    private boolean java;
    private boolean encodingNone;
    private boolean kcodeDefault;
    private boolean literal;
}
