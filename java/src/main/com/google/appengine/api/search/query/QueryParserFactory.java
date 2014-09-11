package com.google.appengine.api.search.query;

import org.antlr.runtime.TokenRewriteStream;

/**
 * A factory which produces {@link QueryParser QueryParsers} for a given
 * token rewrite stream.
 */
public class QueryParserFactory {
  private static final ThreadLocal<QueryParser> PARSER_POOL =
      new ThreadLocal<QueryParser>() {
        @Override
        protected QueryParser initialValue() {
          return new QueryParser(null);
        }
      };

  public QueryParser newParser(TokenRewriteStream tokens) {
    QueryParser parser = PARSER_POOL.get();
    parser.setTokenStream(tokens);
    return parser;
  }
}
