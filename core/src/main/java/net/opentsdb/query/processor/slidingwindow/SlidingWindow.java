// This file is part of OpenTSDB.
// Copyright (C) 2018  The OpenTSDB Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package net.opentsdb.query.processor.slidingwindow;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

import net.opentsdb.data.TimeSeries;
import net.opentsdb.data.TimeSeriesDataType;
import net.opentsdb.data.TimeSeriesId;
import net.opentsdb.data.TimeSeriesValue;
import net.opentsdb.data.TimeSpecification;
import net.opentsdb.data.TypedIterator;
import net.opentsdb.query.AbstractQueryNode;
import net.opentsdb.query.QueryNode;
import net.opentsdb.query.QueryNodeConfig;
import net.opentsdb.query.QueryNodeFactory;
import net.opentsdb.query.QueryPipelineContext;
import net.opentsdb.query.QueryResult;
import net.opentsdb.rollup.RollupConfig;

/**
 * A node that computes an aggregation on a window that slides over the
 * data points per iteration. E.g. this can implement a 5 minute 
 * moving average or a 10 minute max function.
 * <p>
 * The first window starts at the first data point equal to or greater 
 * than the timestamp of the query start time. Each data value emitted
 * represents the aggregation of values from the current values timestamp
 * to the time of the window in the past, exclusive of the window start 
 * timestamp. E.g. with the following data:
 * t1, t2, t3, t4, t5, t6
 * and a window of 5 intervals, the value for t6 would include t2 to t6.
 * 
 * @since 3.0
 */
public class SlidingWindow extends AbstractQueryNode {

  /** The non-null config. */
  private final SlidingWindowConfig config;
  
  /**
   * Default ctor.
   * @param factory The non-null parent factory.
   * @param context The non-null context.
   * @param id An optional ID.
   * @param config The non-null config.
   */
  public SlidingWindow(final QueryNodeFactory factory, 
                       final QueryPipelineContext context,
                       final String id,
                       final SlidingWindowConfig config) {
    super(factory, context, id);
    if (config == null) {
      throw new IllegalArgumentException("Config cannot be null.");
    }
    this.config = config;
  }

  @Override
  public QueryNodeConfig config() {
    return config;
  }

  @Override
  public void close() {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void onComplete(QueryNode downstream, long final_sequence,
      long total_sequences) {
    completeUpstream(final_sequence, total_sequences);
  }

  @Override
  public void onNext(final QueryResult next) {
    sendUpstream(new SlidingWindowResult(next));
  }

  @Override
  public void onError(Throwable t) {
    sendUpstream(t);
  }
  
  /**
   * Local results.
   */
  class SlidingWindowResult implements QueryResult {

    /** The original results. */
    private final QueryResult next;
    
    /** The new series. */
    private List<TimeSeries> series;
    
    SlidingWindowResult(final QueryResult next) {
      this.next = next;
      series = Lists.newArrayListWithExpectedSize(next.timeSeries().size());
      for (final TimeSeries ts : next.timeSeries()) {
        series.add(new SlidingWindowTimeSeries(ts));
      }
    }
    
    @Override
    public TimeSpecification timeSpecification() {
      return next.timeSpecification();
    }

    @Override
    public Collection<TimeSeries> timeSeries() {
      return series;
    }

    @Override
    public long sequenceId() {
      return next.sequenceId();
    }

    @Override
    public QueryNode source() {
      return SlidingWindow.this;
    }

    @Override
    public TypeToken<? extends TimeSeriesId> idType() {
      return next.idType();
    }

    @Override
    public ChronoUnit resolution() {
      return next.resolution();
    }

    @Override
    public RollupConfig rollupConfig() {
      return next.rollupConfig();
    }

    @Override
    public void close() {
      next.close();
    }
    
    /**
     * A nested time series class that injects our iterators.
     */
    class SlidingWindowTimeSeries implements TimeSeries {
      private final TimeSeries source;
      
      SlidingWindowTimeSeries(final TimeSeries source) {
        this.source = source;
      }
      
      @Override
      public TimeSeriesId id() {
        return source.id();
      }

      @Override
      public Optional<Iterator<TimeSeriesValue<? extends TimeSeriesDataType>>> iterator(
          final TypeToken<?> type) {
        if (!source.types().contains(type)) {
          return Optional.empty();
        }
        if (((SlidingWindowFactory) factory).types().contains(type)) {
          final Iterator<TimeSeriesValue<? extends TimeSeriesDataType>> iterator = 
              ((SlidingWindowFactory) factory).newTypedIterator(
                  type, 
                  SlidingWindow.this, 
                  SlidingWindowResult.this, 
                  Lists.newArrayList(source));
          return Optional.of(iterator);
        }
        return source.iterator(type);
      }

      @Override
      public Collection<TypedIterator<TimeSeriesValue<? extends TimeSeriesDataType>>> iterators() {
        final Collection<TypedIterator<TimeSeriesValue<? extends TimeSeriesDataType>>> iterators = Lists.newArrayListWithExpectedSize(source.types().size());
        
        for (final TypeToken<?> type : source.types()) {
          if (((SlidingWindowFactory) factory).types().contains(type)) {
            final TypedIterator<TimeSeriesValue<? extends TimeSeriesDataType>> iterator = 
                ((SlidingWindowFactory) factory).newTypedIterator(
                    type, 
                    SlidingWindow.this, 
                    SlidingWindowResult.this, 
                    Lists.newArrayList(source));
            iterators.add(iterator);
          } else {
            final Optional<Iterator<TimeSeriesValue<? extends TimeSeriesDataType>>> 
              optional = source.iterator(type);
            if (optional.isPresent()) {
              iterators.add((TypedIterator) optional.get());
            }
          }
        }
        return iterators;
      }

      @Override
      public Collection<TypeToken<?>> types() {
        return source.types();
      }

      @Override
      public void close() {
        source.close();
      }
      
    }
  }
}