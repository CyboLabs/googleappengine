

  package com.google.appengine.api.search.query;

import org.antlr.runtime.*;

public class ExpressionLexer extends Lexer {
    public static final int DOLLAR=54;
    public static final int EXPONENT=49;
    public static final int LT=11;
    public static final int LSQUARE=23;
    public static final int ASCII_LETTER=52;
    public static final int LOG=40;
    public static final int SNIPPET=44;
    public static final int OCTAL_ESC=57;
    public static final int MAX=41;
    public static final int COUNT=38;
    public static final int FLOAT=34;
    public static final int NAME_START=50;
    public static final int HTML=28;
    public static final int NOT=10;
    public static final int ATOM=29;
    public static final int AND=7;
    public static final int EOF=-1;
    public static final int LPAREN=21;
    public static final int INDEX=5;
    public static final int QUOTE=47;
    public static final int RPAREN=22;
    public static final int DISTANCE=39;
    public static final int T__58=58;
    public static final int NAME=26;
    public static final int ESC_SEQ=48;
    public static final int POW=43;
    public static final int COMMA=36;
    public static final int PLUS=17;
    public static final int GEO=32;
    public static final int DIGIT=46;
    public static final int EQ=15;
    public static final int NE=16;
    public static final int GE=14;
    public static final int XOR=9;
    public static final int SWITCH=45;
    public static final int UNICODE_ESC=56;
    public static final int NUMBER=31;
    public static final int HEX_DIGIT=55;
    public static final int UNDERSCORE=53;
    public static final int INT=24;
    public static final int MIN=42;
    public static final int TEXT=27;
    public static final int RSQUARE=25;
    public static final int MINUS=18;
    public static final int GEOPOINT=33;
    public static final int PHRASE=35;
    public static final int ABS=37;
    public static final int WS=51;
    public static final int NEG=4;
    public static final int OR=8;
    public static final int GT=13;
    public static final int DIV=20;
    public static final int DATE=30;
    public static final int TIMES=19;
    public static final int COND=6;
    public static final int LE=12;

    public ExpressionLexer() {;}
    public ExpressionLexer(CharStream input) {
        this(input, new RecognizerSharedState());
    }
    public ExpressionLexer(CharStream input, RecognizerSharedState state) {
        super(input,state);

    }
    public String getGrammarFileName() { return ""; }

    public final void mT__58() throws RecognitionException {
        try {
            int _type = T__58;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('.');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mABS() throws RecognitionException {
        try {
            int _type = ABS;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("abs");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mCOUNT() throws RecognitionException {
        try {
            int _type = COUNT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("count");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mDISTANCE() throws RecognitionException {
        try {
            int _type = DISTANCE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("distance");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mGEOPOINT() throws RecognitionException {
        try {
            int _type = GEOPOINT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("geopoint");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mLOG() throws RecognitionException {
        try {
            int _type = LOG;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("log");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mMAX() throws RecognitionException {
        try {
            int _type = MAX;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("max");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mMIN() throws RecognitionException {
        try {
            int _type = MIN;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("min");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mPOW() throws RecognitionException {
        try {
            int _type = POW;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("pow");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mAND() throws RecognitionException {
        try {
            int _type = AND;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("AND");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mOR() throws RecognitionException {
        try {
            int _type = OR;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("OR");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mXOR() throws RecognitionException {
        try {
            int _type = XOR;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("XOR");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mNOT() throws RecognitionException {
        try {
            int _type = NOT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("NOT");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mSNIPPET() throws RecognitionException {
        try {
            int _type = SNIPPET;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("snippet");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mSWITCH() throws RecognitionException {
        try {
            int _type = SWITCH;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("switch");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mTEXT() throws RecognitionException {
        try {
            int _type = TEXT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("text");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mHTML() throws RecognitionException {
        try {
            int _type = HTML;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("html");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mATOM() throws RecognitionException {
        try {
            int _type = ATOM;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("atom");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mDATE() throws RecognitionException {
        try {
            int _type = DATE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("date");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mNUMBER() throws RecognitionException {
        try {
            int _type = NUMBER;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("number");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mGEO() throws RecognitionException {
        try {
            int _type = GEO;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("geo");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mINT() throws RecognitionException {
        try {
            int _type = INT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            int cnt1=0;
            loop1:
            do {
                int alt1=2;
                int LA1_0 = input.LA(1);

                if ( ((LA1_0>='0' && LA1_0<='9')) ) {
                    alt1=1;
                }

                switch (alt1) {
            	case 1 :
            	    {
            	    mDIGIT();

            	    }
            	    break;

            	default :
            	    if ( cnt1 >= 1 ) break loop1;
                        EarlyExitException eee =
                            new EarlyExitException(1, input);
                        throw eee;
                }
                cnt1++;
            } while (true);

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mPHRASE() throws RecognitionException {
        try {
            int _type = PHRASE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            mQUOTE();
            loop2:
            do {
                int alt2=3;
                int LA2_0 = input.LA(1);

                if ( (LA2_0=='\\') ) {
                    alt2=1;
                }
                else if ( ((LA2_0>='\u0000' && LA2_0<='!')||(LA2_0>='#' && LA2_0<='[')||(LA2_0>=']' && LA2_0<='\uFFFF')) ) {
                    alt2=2;
                }

                switch (alt2) {
            	case 1 :
            	    {
            	    mESC_SEQ();

            	    }
            	    break;
            	case 2 :
            	    {
            	    if ( (input.LA(1)>='\u0000' && input.LA(1)<='!')||(input.LA(1)>='#' && input.LA(1)<='[')||(input.LA(1)>=']' && input.LA(1)<='\uFFFF') ) {
            	        input.consume();

            	    }
            	    else {
            	        MismatchedSetException mse = new MismatchedSetException(null,input);
            	        recover(mse);
            	        throw mse;}

            	    }
            	    break;

            	default :
            	    break loop2;
                }
            } while (true);

            mQUOTE();

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mFLOAT() throws RecognitionException {
        try {
            int _type = FLOAT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            int alt9=3;
            alt9 = dfa9.predict(input);
            switch (alt9) {
                case 1 :
                    {
                    int cnt3=0;
                    loop3:
                    do {
                        int alt3=2;
                        int LA3_0 = input.LA(1);

                        if ( ((LA3_0>='0' && LA3_0<='9')) ) {
                            alt3=1;
                        }

                        switch (alt3) {
                    	case 1 :
                    	    {
                    	    mDIGIT();

                    	    }
                    	    break;

                    	default :
                    	    if ( cnt3 >= 1 ) break loop3;
                                EarlyExitException eee =
                                    new EarlyExitException(3, input);
                                throw eee;
                        }
                        cnt3++;
                    } while (true);

                    match('.');
                    loop4:
                    do {
                        int alt4=2;
                        int LA4_0 = input.LA(1);

                        if ( ((LA4_0>='0' && LA4_0<='9')) ) {
                            alt4=1;
                        }

                        switch (alt4) {
                    	case 1 :
                    	    {
                    	    mDIGIT();

                    	    }
                    	    break;

                    	default :
                    	    break loop4;
                        }
                    } while (true);

                    int alt5=2;
                    int LA5_0 = input.LA(1);

                    if ( (LA5_0=='E'||LA5_0=='e') ) {
                        alt5=1;
                    }
                    switch (alt5) {
                        case 1 :
                            {
                            mEXPONENT();

                            }
                            break;

                    }

                    }
                    break;
                case 2 :
                    {
                    match('.');
                    int cnt6=0;
                    loop6:
                    do {
                        int alt6=2;
                        int LA6_0 = input.LA(1);

                        if ( ((LA6_0>='0' && LA6_0<='9')) ) {
                            alt6=1;
                        }

                        switch (alt6) {
                    	case 1 :
                    	    {
                    	    mDIGIT();

                    	    }
                    	    break;

                    	default :
                    	    if ( cnt6 >= 1 ) break loop6;
                                EarlyExitException eee =
                                    new EarlyExitException(6, input);
                                throw eee;
                        }
                        cnt6++;
                    } while (true);

                    int alt7=2;
                    int LA7_0 = input.LA(1);

                    if ( (LA7_0=='E'||LA7_0=='e') ) {
                        alt7=1;
                    }
                    switch (alt7) {
                        case 1 :
                            {
                            mEXPONENT();

                            }
                            break;

                    }

                    }
                    break;
                case 3 :
                    {
                    int cnt8=0;
                    loop8:
                    do {
                        int alt8=2;
                        int LA8_0 = input.LA(1);

                        if ( ((LA8_0>='0' && LA8_0<='9')) ) {
                            alt8=1;
                        }

                        switch (alt8) {
                    	case 1 :
                    	    {
                    	    mDIGIT();

                    	    }
                    	    break;

                    	default :
                    	    if ( cnt8 >= 1 ) break loop8;
                                EarlyExitException eee =
                                    new EarlyExitException(8, input);
                                throw eee;
                        }
                        cnt8++;
                    } while (true);

                    mEXPONENT();

                    }
                    break;

            }
            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mNAME() throws RecognitionException {
        try {
            int _type = NAME;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            mNAME_START();
            loop10:
            do {
                int alt10=2;
                int LA10_0 = input.LA(1);

                if ( (LA10_0=='$'||(LA10_0>='0' && LA10_0<='9')||(LA10_0>='A' && LA10_0<='Z')||LA10_0=='_'||(LA10_0>='a' && LA10_0<='z')) ) {
                    alt10=1;
                }

                switch (alt10) {
            	case 1 :
            	    {
            	    if ( input.LA(1)=='$'||(input.LA(1)>='0' && input.LA(1)<='9')||(input.LA(1)>='A' && input.LA(1)<='Z')||input.LA(1)=='_'||(input.LA(1)>='a' && input.LA(1)<='z') ) {
            	        input.consume();

            	    }
            	    else {
            	        MismatchedSetException mse = new MismatchedSetException(null,input);
            	        recover(mse);
            	        throw mse;}

            	    }
            	    break;

            	default :
            	    break loop10;
                }
            } while (true);

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mLPAREN() throws RecognitionException {
        try {
            int _type = LPAREN;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('(');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mRPAREN() throws RecognitionException {
        try {
            int _type = RPAREN;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match(')');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mLSQUARE() throws RecognitionException {
        try {
            int _type = LSQUARE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('[');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mRSQUARE() throws RecognitionException {
        try {
            int _type = RSQUARE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match(']');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mPLUS() throws RecognitionException {
        try {
            int _type = PLUS;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('+');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mMINUS() throws RecognitionException {
        try {
            int _type = MINUS;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('-');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mTIMES() throws RecognitionException {
        try {
            int _type = TIMES;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('*');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mDIV() throws RecognitionException {
        try {
            int _type = DIV;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('/');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mLT() throws RecognitionException {
        try {
            int _type = LT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('<');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mLE() throws RecognitionException {
        try {
            int _type = LE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("<=");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mGT() throws RecognitionException {
        try {
            int _type = GT;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('>');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mGE() throws RecognitionException {
        try {
            int _type = GE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match(">=");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mEQ() throws RecognitionException {
        try {
            int _type = EQ;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('=');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mNE() throws RecognitionException {
        try {
            int _type = NE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match("!=");

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mCOND() throws RecognitionException {
        try {
            int _type = COND;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('?');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mQUOTE() throws RecognitionException {
        try {
            int _type = QUOTE;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match('\"');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mCOMMA() throws RecognitionException {
        try {
            int _type = COMMA;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            match(',');

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mWS() throws RecognitionException {
        try {
            int _type = WS;
            int _channel = DEFAULT_TOKEN_CHANNEL;
            {
            int cnt11=0;
            loop11:
            do {
                int alt11=2;
                int LA11_0 = input.LA(1);

                if ( ((LA11_0>='\t' && LA11_0<='\n')||LA11_0=='\r'||LA11_0==' ') ) {
                    alt11=1;
                }

                switch (alt11) {
            	case 1 :
            	    {
            	    if ( (input.LA(1)>='\t' && input.LA(1)<='\n')||input.LA(1)=='\r'||input.LA(1)==' ' ) {
            	        input.consume();

            	    }
            	    else {
            	        MismatchedSetException mse = new MismatchedSetException(null,input);
            	        recover(mse);
            	        throw mse;}

            	    }
            	    break;

            	default :
            	    if ( cnt11 >= 1 ) break loop11;
                        EarlyExitException eee =
                            new EarlyExitException(11, input);
                        throw eee;
                }
                cnt11++;
            } while (true);

            _channel = HIDDEN;

            }

            state.type = _type;
            state.channel = _channel;
        }
        finally {
        }
    }

    public final void mEXPONENT() throws RecognitionException {
        try {
            {
            if ( input.LA(1)=='E'||input.LA(1)=='e' ) {
                input.consume();

            }
            else {
                MismatchedSetException mse = new MismatchedSetException(null,input);
                recover(mse);
                throw mse;}

            int alt12=2;
            int LA12_0 = input.LA(1);

            if ( (LA12_0=='+'||LA12_0=='-') ) {
                alt12=1;
            }
            switch (alt12) {
                case 1 :
                    {
                    if ( input.LA(1)=='+'||input.LA(1)=='-' ) {
                        input.consume();

                    }
                    else {
                        MismatchedSetException mse = new MismatchedSetException(null,input);
                        recover(mse);
                        throw mse;}

                    }
                    break;

            }

            int cnt13=0;
            loop13:
            do {
                int alt13=2;
                int LA13_0 = input.LA(1);

                if ( ((LA13_0>='0' && LA13_0<='9')) ) {
                    alt13=1;
                }

                switch (alt13) {
            	case 1 :
            	    {
            	    mDIGIT();

            	    }
            	    break;

            	default :
            	    if ( cnt13 >= 1 ) break loop13;
                        EarlyExitException eee =
                            new EarlyExitException(13, input);
                        throw eee;
                }
                cnt13++;
            } while (true);

            }

        }
        finally {
        }
    }

    public final void mNAME_START() throws RecognitionException {
        try {
            {
            if ( input.LA(1)=='$'||(input.LA(1)>='A' && input.LA(1)<='Z')||input.LA(1)=='_'||(input.LA(1)>='a' && input.LA(1)<='z') ) {
                input.consume();

            }
            else {
                MismatchedSetException mse = new MismatchedSetException(null,input);
                recover(mse);
                throw mse;}

            }

        }
        finally {
        }
    }

    public final void mASCII_LETTER() throws RecognitionException {
        try {
            {
            if ( (input.LA(1)>='A' && input.LA(1)<='Z')||(input.LA(1)>='a' && input.LA(1)<='z') ) {
                input.consume();

            }
            else {
                MismatchedSetException mse = new MismatchedSetException(null,input);
                recover(mse);
                throw mse;}

            }

        }
        finally {
        }
    }

    public final void mDIGIT() throws RecognitionException {
        try {
            {
            matchRange('0','9');

            }

        }
        finally {
        }
    }

    public final void mDOLLAR() throws RecognitionException {
        try {
            {
            match('$');

            }

        }
        finally {
        }
    }

    public final void mUNDERSCORE() throws RecognitionException {
        try {
            {
            match('_');

            }

        }
        finally {
        }
    }

    public final void mHEX_DIGIT() throws RecognitionException {
        try {
            {
            if ( (input.LA(1)>='0' && input.LA(1)<='9')||(input.LA(1)>='A' && input.LA(1)<='F')||(input.LA(1)>='a' && input.LA(1)<='f') ) {
                input.consume();

            }
            else {
                MismatchedSetException mse = new MismatchedSetException(null,input);
                recover(mse);
                throw mse;}

            }

        }
        finally {
        }
    }

    public final void mESC_SEQ() throws RecognitionException {
        try {
            int alt14=3;
            int LA14_0 = input.LA(1);

            if ( (LA14_0=='\\') ) {
                switch ( input.LA(2) ) {
                case '\"':
                case '\'':
                case '\\':
                case 'b':
                case 'f':
                case 'n':
                case 'r':
                case 't':
                    {
                    alt14=1;
                    }
                    break;
                case 'u':
                    {
                    alt14=2;
                    }
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    {
                    alt14=3;
                    }
                    break;
                default:
                    NoViableAltException nvae =
                        new NoViableAltException("", 14, 1, input);

                    throw nvae;
                }

            }
            else {
                NoViableAltException nvae =
                    new NoViableAltException("", 14, 0, input);

                throw nvae;
            }
            switch (alt14) {
                case 1 :
                    {
                    match('\\');
                    if ( input.LA(1)=='\"'||input.LA(1)=='\''||input.LA(1)=='\\'||input.LA(1)=='b'||input.LA(1)=='f'||input.LA(1)=='n'||input.LA(1)=='r'||input.LA(1)=='t' ) {
                        input.consume();

                    }
                    else {
                        MismatchedSetException mse = new MismatchedSetException(null,input);
                        recover(mse);
                        throw mse;}

                    }
                    break;
                case 2 :
                    {
                    mUNICODE_ESC();

                    }
                    break;
                case 3 :
                    {
                    mOCTAL_ESC();

                    }
                    break;

            }
        }
        finally {
        }
    }

    public final void mOCTAL_ESC() throws RecognitionException {
        try {
            int alt15=3;
            int LA15_0 = input.LA(1);

            if ( (LA15_0=='\\') ) {
                int LA15_1 = input.LA(2);

                if ( ((LA15_1>='0' && LA15_1<='3')) ) {
                    int LA15_2 = input.LA(3);

                    if ( ((LA15_2>='0' && LA15_2<='7')) ) {
                        int LA15_4 = input.LA(4);

                        if ( ((LA15_4>='0' && LA15_4<='7')) ) {
                            alt15=1;
                        }
                        else {
                            alt15=2;}
                    }
                    else {
                        alt15=3;}
                }
                else if ( ((LA15_1>='4' && LA15_1<='7')) ) {
                    int LA15_3 = input.LA(3);

                    if ( ((LA15_3>='0' && LA15_3<='7')) ) {
                        alt15=2;
                    }
                    else {
                        alt15=3;}
                }
                else {
                    NoViableAltException nvae =
                        new NoViableAltException("", 15, 1, input);

                    throw nvae;
                }
            }
            else {
                NoViableAltException nvae =
                    new NoViableAltException("", 15, 0, input);

                throw nvae;
            }
            switch (alt15) {
                case 1 :
                    {
                    match('\\');
                    {
                    matchRange('0','3');

                    }

                    {
                    matchRange('0','7');

                    }

                    {
                    matchRange('0','7');

                    }

                    }
                    break;
                case 2 :
                    {
                    match('\\');
                    {
                    matchRange('0','7');

                    }

                    {
                    matchRange('0','7');

                    }

                    }
                    break;
                case 3 :
                    {
                    match('\\');
                    {
                    matchRange('0','7');

                    }

                    }
                    break;

            }
        }
        finally {
        }
    }

    public final void mUNICODE_ESC() throws RecognitionException {
        try {
            {
            match('\\');
            match('u');
            mHEX_DIGIT();
            mHEX_DIGIT();
            mHEX_DIGIT();
            mHEX_DIGIT();

            }

        }
        finally {
        }
    }

    public void mTokens() throws RecognitionException {
        int alt16=43;
        alt16 = dfa16.predict(input);
        switch (alt16) {
            case 1 :
                {
                mT__58();

                }
                break;
            case 2 :
                {
                mABS();

                }
                break;
            case 3 :
                {
                mCOUNT();

                }
                break;
            case 4 :
                {
                mDISTANCE();

                }
                break;
            case 5 :
                {
                mGEOPOINT();

                }
                break;
            case 6 :
                {
                mLOG();

                }
                break;
            case 7 :
                {
                mMAX();

                }
                break;
            case 8 :
                {
                mMIN();

                }
                break;
            case 9 :
                {
                mPOW();

                }
                break;
            case 10 :
                {
                mAND();

                }
                break;
            case 11 :
                {
                mOR();

                }
                break;
            case 12 :
                {
                mXOR();

                }
                break;
            case 13 :
                {
                mNOT();

                }
                break;
            case 14 :
                {
                mSNIPPET();

                }
                break;
            case 15 :
                {
                mSWITCH();

                }
                break;
            case 16 :
                {
                mTEXT();

                }
                break;
            case 17 :
                {
                mHTML();

                }
                break;
            case 18 :
                {
                mATOM();

                }
                break;
            case 19 :
                {
                mDATE();

                }
                break;
            case 20 :
                {
                mNUMBER();

                }
                break;
            case 21 :
                {
                mGEO();

                }
                break;
            case 22 :
                {
                mINT();

                }
                break;
            case 23 :
                {
                mPHRASE();

                }
                break;
            case 24 :
                {
                mFLOAT();

                }
                break;
            case 25 :
                {
                mNAME();

                }
                break;
            case 26 :
                {
                mLPAREN();

                }
                break;
            case 27 :
                {
                mRPAREN();

                }
                break;
            case 28 :
                {
                mLSQUARE();

                }
                break;
            case 29 :
                {
                mRSQUARE();

                }
                break;
            case 30 :
                {
                mPLUS();

                }
                break;
            case 31 :
                {
                mMINUS();

                }
                break;
            case 32 :
                {
                mTIMES();

                }
                break;
            case 33 :
                {
                mDIV();

                }
                break;
            case 34 :
                {
                mLT();

                }
                break;
            case 35 :
                {
                mLE();

                }
                break;
            case 36 :
                {
                mGT();

                }
                break;
            case 37 :
                {
                mGE();

                }
                break;
            case 38 :
                {
                mEQ();

                }
                break;
            case 39 :
                {
                mNE();

                }
                break;
            case 40 :
                {
                mCOND();

                }
                break;
            case 41 :
                {
                mQUOTE();

                }
                break;
            case 42 :
                {
                mCOMMA();

                }
                break;
            case 43 :
                {
                mWS();

                }
                break;

        }

    }

    protected DFA9 dfa9 = new DFA9(this);
    protected DFA16 dfa16 = new DFA16(this);
    static final String DFA9_eotS =
        "\5\uffff";
    static final String DFA9_eofS =
        "\5\uffff";
    static final String DFA9_minS =
        "\2\56\3\uffff";
    static final String DFA9_maxS =
        "\1\71\1\145\3\uffff";
    static final String DFA9_acceptS =
        "\2\uffff\1\2\1\1\1\3";
    static final String DFA9_specialS =
        "\5\uffff}>";
    static final String[] DFA9_transitionS = {
            "\1\2\1\uffff\12\1",
            "\1\3\1\uffff\12\1\13\uffff\1\4\37\uffff\1\4",
            "",
            "",
            ""
    };

    static final short[] DFA9_eot = DFA.unpackEncodedString(DFA9_eotS);
    static final short[] DFA9_eof = DFA.unpackEncodedString(DFA9_eofS);
    static final char[] DFA9_min = DFA.unpackEncodedStringToUnsignedChars(DFA9_minS);
    static final char[] DFA9_max = DFA.unpackEncodedStringToUnsignedChars(DFA9_maxS);
    static final short[] DFA9_accept = DFA.unpackEncodedString(DFA9_acceptS);
    static final short[] DFA9_special = DFA.unpackEncodedString(DFA9_specialS);
    static final short[][] DFA9_transition;

    static {
        int numStates = DFA9_transitionS.length;
        DFA9_transition = new short[numStates][];
        for (int i=0; i<numStates; i++) {
            DFA9_transition[i] = DFA.unpackEncodedString(DFA9_transitionS[i]);
        }
    }

    class DFA9 extends DFA {

        public DFA9(BaseRecognizer recognizer) {
            this.recognizer = recognizer;
            this.decisionNumber = 9;
            this.eot = DFA9_eot;
            this.eof = DFA9_eof;
            this.min = DFA9_min;
            this.max = DFA9_max;
            this.accept = DFA9_accept;
            this.special = DFA9_special;
            this.transition = DFA9_transition;
        }
        public String getDescription() {
            return "261:1: FLOAT : ( ( DIGIT )+ '.' ( DIGIT )* ( EXPONENT )? | '.' ( DIGIT )+ ( EXPONENT )? | ( DIGIT )+ EXPONENT );";
        }
    }
    static final String DFA16_eotS =
        "\1\uffff\1\43\17\23\1\70\1\71\11\uffff\1\74\1\76\7\uffff\13\23\1"+
        "\112\7\23\7\uffff\1\122\4\23\1\130\1\131\1\132\1\133\1\134\1\135"+
        "\1\uffff\1\136\1\137\5\23\1\uffff\1\145\2\23\1\150\1\23\10\uffff"+
        "\2\23\1\154\1\155\1\23\1\uffff\1\157\1\23\1\uffff\3\23\2\uffff\1"+
        "\23\1\uffff\3\23\1\170\1\171\2\23\1\174\2\uffff\1\175\1\176\3\uffff";
    static final String DFA16_eofS =
        "\177\uffff";
    static final String DFA16_minS =
        "\1\11\1\60\1\142\1\157\1\141\1\145\1\157\1\141\1\157\1\116\1\122"+
        "\2\117\1\156\1\145\1\164\1\165\1\56\1\0\11\uffff\2\75\7\uffff\1"+
        "\163\1\157\1\165\1\163\1\164\1\157\1\147\1\170\1\156\1\167\1\104"+
        "\1\44\1\122\1\124\2\151\1\170\2\155\7\uffff\1\44\1\155\1\156\1\164"+
        "\1\145\6\44\1\uffff\2\44\1\160\2\164\1\154\1\142\1\uffff\1\44\1"+
        "\164\1\141\1\44\1\157\10\uffff\1\160\1\143\2\44\1\145\1\uffff\1"+
        "\44\1\156\1\uffff\1\151\1\145\1\150\2\uffff\1\162\1\uffff\1\143"+
        "\1\156\1\164\2\44\1\145\1\164\1\44\2\uffff\2\44\3\uffff";
    static final String DFA16_maxS =
        "\1\172\1\71\1\164\1\157\1\151\1\145\1\157\1\151\1\157\1\116\1\122"+
        "\2\117\1\167\1\145\1\164\1\165\1\145\1\uffff\11\uffff\2\75\7\uffff"+
        "\1\163\1\157\1\165\1\163\1\164\1\157\1\147\1\170\1\156\1\167\1\104"+
        "\1\172\1\122\1\124\2\151\1\170\2\155\7\uffff\1\172\1\155\1\156\1"+
        "\164\1\145\6\172\1\uffff\2\172\1\160\2\164\1\154\1\142\1\uffff\1"+
        "\172\1\164\1\141\1\172\1\157\10\uffff\1\160\1\143\2\172\1\145\1"+
        "\uffff\1\172\1\156\1\uffff\1\151\1\145\1\150\2\uffff\1\162\1\uffff"+
        "\1\143\1\156\1\164\2\172\1\145\1\164\1\172\2\uffff\2\172\3\uffff";
    static final String DFA16_acceptS =
        "\23\uffff\1\31\1\32\1\33\1\34\1\35\1\36\1\37\1\40\1\41\2\uffff\1"+
        "\46\1\47\1\50\1\52\1\53\1\1\1\30\23\uffff\1\26\1\51\1\27\1\43\1"+
        "\42\1\45\1\44\13\uffff\1\13\7\uffff\1\2\5\uffff\1\25\1\6\1\7\1\10"+
        "\1\11\1\12\1\14\1\15\5\uffff\1\22\2\uffff\1\23\3\uffff\1\20\1\21"+
        "\1\uffff\1\3\10\uffff\1\17\1\24\2\uffff\1\16\1\4\1\5";
    static final String DFA16_specialS =
        "\22\uffff\1\0\154\uffff}>";
    static final String[] DFA16_transitionS = {
            "\2\42\2\uffff\1\42\22\uffff\1\42\1\37\1\22\1\uffff\1\23\3\uffff"+
            "\1\24\1\25\1\32\1\30\1\41\1\31\1\1\1\33\12\21\2\uffff\1\34\1"+
            "\36\1\35\1\40\1\uffff\1\11\14\23\1\14\1\12\10\23\1\13\2\23\1"+
            "\26\1\uffff\1\27\1\uffff\1\23\1\uffff\1\2\1\23\1\3\1\4\2\23"+
            "\1\5\1\17\3\23\1\6\1\7\1\20\1\23\1\10\2\23\1\15\1\16\6\23",
            "\12\44",
            "\1\45\21\uffff\1\46",
            "\1\47",
            "\1\51\7\uffff\1\50",
            "\1\52",
            "\1\53",
            "\1\54\7\uffff\1\55",
            "\1\56",
            "\1\57",
            "\1\60",
            "\1\61",
            "\1\62",
            "\1\63\10\uffff\1\64",
            "\1\65",
            "\1\66",
            "\1\67",
            "\1\44\1\uffff\12\21\13\uffff\1\44\37\uffff\1\44",
            "\0\72",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "\1\73",
            "\1\75",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "\1\77",
            "\1\100",
            "\1\101",
            "\1\102",
            "\1\103",
            "\1\104",
            "\1\105",
            "\1\106",
            "\1\107",
            "\1\110",
            "\1\111",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\113",
            "\1\114",
            "\1\115",
            "\1\116",
            "\1\117",
            "\1\120",
            "\1\121",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\123",
            "\1\124",
            "\1\125",
            "\1\126",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\17"+
            "\23\1\127\12\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\140",
            "\1\141",
            "\1\142",
            "\1\143",
            "\1\144",
            "",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\146",
            "\1\147",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\151",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "\1\152",
            "\1\153",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\156",
            "",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\160",
            "",
            "\1\161",
            "\1\162",
            "\1\163",
            "",
            "",
            "\1\164",
            "",
            "\1\165",
            "\1\166",
            "\1\167",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\172",
            "\1\173",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "",
            "",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "\1\23\13\uffff\12\23\7\uffff\32\23\4\uffff\1\23\1\uffff\32"+
            "\23",
            "",
            "",
            ""
    };

    static final short[] DFA16_eot = DFA.unpackEncodedString(DFA16_eotS);
    static final short[] DFA16_eof = DFA.unpackEncodedString(DFA16_eofS);
    static final char[] DFA16_min = DFA.unpackEncodedStringToUnsignedChars(DFA16_minS);
    static final char[] DFA16_max = DFA.unpackEncodedStringToUnsignedChars(DFA16_maxS);
    static final short[] DFA16_accept = DFA.unpackEncodedString(DFA16_acceptS);
    static final short[] DFA16_special = DFA.unpackEncodedString(DFA16_specialS);
    static final short[][] DFA16_transition;

    static {
        int numStates = DFA16_transitionS.length;
        DFA16_transition = new short[numStates][];
        for (int i=0; i<numStates; i++) {
            DFA16_transition[i] = DFA.unpackEncodedString(DFA16_transitionS[i]);
        }
    }

    class DFA16 extends DFA {

        public DFA16(BaseRecognizer recognizer) {
            this.recognizer = recognizer;
            this.decisionNumber = 16;
            this.eot = DFA16_eot;
            this.eof = DFA16_eof;
            this.min = DFA16_min;
            this.max = DFA16_max;
            this.accept = DFA16_accept;
            this.special = DFA16_special;
            this.transition = DFA16_transition;
        }
        public String getDescription() {
            return "1:1: Tokens : ( T__58 | ABS | COUNT | DISTANCE | GEOPOINT | LOG | MAX | MIN | POW | AND | OR | XOR | NOT | SNIPPET | SWITCH | TEXT | HTML | ATOM | DATE | NUMBER | GEO | INT | PHRASE | FLOAT | NAME | LPAREN | RPAREN | LSQUARE | RSQUARE | PLUS | MINUS | TIMES | DIV | LT | LE | GT | GE | EQ | NE | COND | QUOTE | COMMA | WS );";
        }
        public int specialStateTransition(int s, IntStream _input) throws NoViableAltException {
            IntStream input = _input;
        	int _s = s;
            switch ( s ) {
                    case 0 :
                        int LA16_18 = input.LA(1);

                        s = -1;
                        if ( ((LA16_18>='\u0000' && LA16_18<='\uFFFF')) ) {s = 58;}

                        else s = 57;

                        if ( s>=0 ) return s;
                        break;
            }
            NoViableAltException nvae =
                new NoViableAltException(getDescription(), 16, _s, input);
            error(nvae);
            throw nvae;
        }
    }

}
