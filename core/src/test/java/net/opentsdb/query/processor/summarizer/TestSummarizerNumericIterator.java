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
package net.opentsdb.query.processor.summarizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Lists;

import net.opentsdb.core.MockTSDB;
import net.opentsdb.core.MockTSDBDefault;
import net.opentsdb.data.BaseTimeSeriesStringId;
import net.opentsdb.data.MockTimeSeries;
import net.opentsdb.data.SecondTimeStamp;
import net.opentsdb.data.TimeSeries;
import net.opentsdb.data.TimeSeriesValue;
import net.opentsdb.data.types.numeric.MutableNumericValue;
import net.opentsdb.data.types.numeric.NumericArrayTimeSeries;
import net.opentsdb.data.types.numeric.NumericSummaryType;
import net.opentsdb.query.QueryNode;
import net.opentsdb.query.QueryPipelineContext;
import net.opentsdb.query.QueryResult;
import net.opentsdb.rollup.DefaultRollupConfig;
import net.opentsdb.rollup.RollupConfig;
import net.opentsdb.rollup.RollupInterval;

public class TestSummarizerNumericIterator {
  public static MockTSDB TSDB;
  
  private QueryNode node;
  private QueryResult result;
  private SummarizerConfig config;
  private RollupConfig rollup_config;
  
  @BeforeClass
  public static void beforeClass() {
    TSDB = MockTSDBDefault.getMockTSDB();
  }
  
  @Before
  public void before() throws Exception {
    node = mock(QueryNode.class);
    result = mock(QueryResult.class);
    
    config = (SummarizerConfig) 
        SummarizerConfig.newBuilder()
        .setSummaries(Lists.newArrayList("sum", "avg", "max", "min", "count"))
        .setId("summarizer")
        .build();
    
    rollup_config = DefaultRollupConfig.newBuilder()
        .addAggregationId("sum", 0)
        .addAggregationId("count", 1)
        .addAggregationId("max", 2)
        .addAggregationId("min", 3)
        .addAggregationId("avg", 5)
        .addInterval(RollupInterval.builder()
            .setInterval("sum")
            .setTable("tsdb")
            .setPreAggregationTable("tsdb")
            .setInterval("1h")
            .setRowSpan("1d"))
        .build();
    
    when(result.source()).thenReturn(node);
    when(result.rollupConfig()).thenReturn(rollup_config);
    when(node.config()).thenReturn(config);
    QueryPipelineContext context = mock(QueryPipelineContext.class);
    when(node.pipelineContext()).thenReturn(context);
    when(context.tsdb()).thenReturn(TSDB);
  }
  
  @Test
  public void ctor() throws Exception {
    TimeSeries series = new MockTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build());
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(0L), 42));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(60L), 24));
    
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    // empty
    series = new MockTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build());
    iterator = new SummarizerNumericIterator(node, result, series);
    assertFalse(iterator.hasNext());
  }
  
  @Test
  public void numericTypeLongs() throws Exception {
    TimeSeries series = new MockTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build());
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(0L), 42));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(60L), 24));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(120L), -8));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(240L), 1));
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    TimeSeriesValue<NumericSummaryType> value = 
        (TimeSeriesValue<NumericSummaryType>) iterator.next();
    assertEquals(0, value.timestamp().epoch());
    
    NumericSummaryType summary = value.value();
    assertEquals(5, summary.summariesAvailable().size());
    assertEquals(59, summary.value(0).longValue());
    assertEquals(4, summary.value(1).longValue());
    assertEquals(42, summary.value(2).longValue());
    assertEquals(-8, summary.value(3).longValue());
    assertEquals(14.75, summary.value(5).doubleValue(), 0.001);
    
    assertFalse(iterator.hasNext());
  }
  
  @Test
  public void numericTypeDoubles() throws Exception {
    TimeSeries series = new MockTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build());
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(0L), 42.5));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(60L), 24.75));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(120L), -8.3));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(240L), 1.2));
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    TimeSeriesValue<NumericSummaryType> value = 
        (TimeSeriesValue<NumericSummaryType>) iterator.next();
    assertEquals(0, value.timestamp().epoch());
    
    NumericSummaryType summary = value.value();
    assertEquals(5, summary.summariesAvailable().size());
    assertEquals(60.15, summary.value(0).doubleValue(), 0.001);
    assertEquals(4, summary.value(1).longValue());
    assertEquals(42.5, summary.value(2).doubleValue(), 0.001);
    assertEquals(-8.3, summary.value(3).doubleValue(), 0.001);
    assertEquals(15.037, summary.value(5).doubleValue(), 0.001);
    
    assertFalse(iterator.hasNext());
  }
  
  @Test
  public void numericTypeLongToDouble() throws Exception {
    TimeSeries series = new MockTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build());
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(0L), 42));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(60L), 24));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(120L), -8.3));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(240L), 1.2));
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    TimeSeriesValue<NumericSummaryType> value = 
        (TimeSeriesValue<NumericSummaryType>) iterator.next();
    assertEquals(0, value.timestamp().epoch());
    
    NumericSummaryType summary = value.value();
    assertEquals(5, summary.summariesAvailable().size());
    assertEquals(58.9, summary.value(0).doubleValue(), 0.001);
    assertEquals(4, summary.value(1).longValue());
    assertEquals(42, summary.value(2).doubleValue(), 0.001);
    assertEquals(-8.3, summary.value(3).doubleValue(), 0.001);
    assertEquals(14.725, summary.value(5).doubleValue(), 0.001);
    
    assertFalse(iterator.hasNext());
  }

  @Test
  public void numericTypeDoublesNaNs() throws Exception {
    TimeSeries series = new MockTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build());
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(0L), 42.5));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(60L), Double.NaN));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(120L), Double.NaN));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(240L), 1.2));
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    TimeSeriesValue<NumericSummaryType> value = 
        (TimeSeriesValue<NumericSummaryType>) iterator.next();
    assertEquals(0, value.timestamp().epoch());
    
    NumericSummaryType summary = value.value();
    assertEquals(5, summary.summariesAvailable().size());
    assertEquals(43.7, summary.value(0).doubleValue(), 0.001);
    assertEquals(2, summary.value(1).longValue());
    assertEquals(42.5, summary.value(2).doubleValue(), 0.001);
    assertEquals(1.2, summary.value(3).doubleValue(), 0.001);
    assertEquals(21.85, summary.value(5).doubleValue(), 0.001);
    
    assertFalse(iterator.hasNext());
  }
  
  @Test
  public void numericTypeDoublesNaNsInfectious() throws Exception {
    config = (SummarizerConfig) 
        SummarizerConfig.newBuilder()
        .setSummaries(Lists.newArrayList("sum", "avg", "max", "min", "count"))
        .setInfectiousNan(true)
        .setId("summarizer")
        .build();
    when(node.config()).thenReturn(config);
    
    TimeSeries series = new MockTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build());
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(0L), 42.5));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(60L), Double.NaN));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(120L), Double.NaN));
    ((MockTimeSeries) series).addValue(
        new MutableNumericValue(new SecondTimeStamp(240L), 1.2));
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    TimeSeriesValue<NumericSummaryType> value = 
        (TimeSeriesValue<NumericSummaryType>) iterator.next();
    assertEquals(0, value.timestamp().epoch());
    
    NumericSummaryType summary = value.value();
    assertEquals(5, summary.summariesAvailable().size());
    assertTrue(Double.isNaN(summary.value(0).doubleValue()));
    assertEquals(4, summary.value(1).longValue());
    assertTrue(Double.isNaN(summary.value(2).doubleValue()));
    assertTrue(Double.isNaN(summary.value(3).doubleValue()));
    assertTrue(Double.isNaN(summary.value(5).doubleValue()));
    
    assertFalse(iterator.hasNext());
  }

  @Test
  public void numericArrayTypeLongs() throws Exception {
    TimeSeries series = new NumericArrayTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build(), new SecondTimeStamp(0L));
    ((NumericArrayTimeSeries) series).add(42);
    ((NumericArrayTimeSeries) series).add(24);
    ((NumericArrayTimeSeries) series).add(-8);
    ((NumericArrayTimeSeries) series).add(1);
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    TimeSeriesValue<NumericSummaryType> value = 
        (TimeSeriesValue<NumericSummaryType>) iterator.next();
    assertEquals(0, value.timestamp().epoch());
    
    NumericSummaryType summary = value.value();
    assertEquals(5, summary.summariesAvailable().size());
    assertEquals(59, summary.value(0).longValue());
    assertEquals(4, summary.value(1).longValue());
    assertEquals(42, summary.value(2).longValue());
    assertEquals(-8, summary.value(3).longValue());
    assertEquals(14.75, summary.value(5).doubleValue(), 0.001);
    
    assertFalse(iterator.hasNext());
  }
  
  @Test
  public void numericArrayTypeDoubles() throws Exception {
    TimeSeries series = new NumericArrayTimeSeries(new BaseTimeSeriesStringId.Builder()
        .setMetric("foo")
        .build(), new SecondTimeStamp(0L));
    ((NumericArrayTimeSeries) series).add(42.5);
    ((NumericArrayTimeSeries) series).add(24.75);
    ((NumericArrayTimeSeries) series).add(-8.3);
    ((NumericArrayTimeSeries) series).add(1.2);
    SummarizerNumericIterator iterator = 
        new SummarizerNumericIterator(node, result, series);
    assertTrue(iterator.hasNext());
    
    TimeSeriesValue<NumericSummaryType> value = 
        (TimeSeriesValue<NumericSummaryType>) iterator.next();
    assertEquals(0, value.timestamp().epoch());
    
    NumericSummaryType summary = value.value();
    assertEquals(5, summary.summariesAvailable().size());
    assertEquals(60.15, summary.value(0).doubleValue(), 0.001);
    assertEquals(4, summary.value(1).longValue());
    assertEquals(42.5, summary.value(2).doubleValue(), 0.001);
    assertEquals(-8.3, summary.value(3).doubleValue(), 0.001);
    assertEquals(15.037, summary.value(5).doubleValue(), 0.001);
    
    assertFalse(iterator.hasNext());
  }
}