/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.query.util;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.util.DateUtils;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.types.ExpressionUtils;
import com.mysema.query.types.Predicate;
import com.mysema.query.types.expr.BooleanExpression;
import com.mysema.query.types.path.DateTimePath;
import com.mysema.query.types.path.StringPath;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */
class MatchDateTimeRange {
    
    static enum ComparisonOperator {
        GT, GE, EQ, LT, LTE;
    }
    
    static enum FormatDate {
        DA {
            @Override
            String format(Date date) {
                return DateUtils.formatDA(null, date);
            }
        },
        TM {
            @Override
            String format(Date date) {
                return DateUtils.formatTM(null, date);
            }
        },
        DT {
            @Override
            String format(Date date) {
                return DateUtils.formatDT(null, date);
            }
        };
        abstract String format(Date date);
    }

    static Predicate rangeMatch(StringPath path,
            Attributes keys, int tag, FormatDate dt,
            boolean matchUnknown) {
        DateRange dateRange = keys.getDateRange(tag, null);
        if (dateRange == null)
            return null;
        
        return matchUnknown(path, matchUnknown, range(path, dateRange, dt));
    }

    static Predicate rangeMatch(DateTimePath<Date> dateTimeField, int dateTag, int timeTag,
            long dateAndTimeTag, Attributes keys, boolean combinedDatetimeMatching, boolean matchUnknown) {
        final boolean containsDateTag = keys.containsValue(dateTag);
        final boolean containsTimeTag = keys.containsValue(timeTag);
        if (!containsDateTag && !containsTimeTag)
            return null;
        
        BooleanBuilder predicates = new BooleanBuilder();
        if (containsDateTag && containsTimeTag && combinedDatetimeMatching) {
            predicates.and(matchUnknown 
                    ? ExpressionUtils.or(combinedRange(dateTimeField, keys.getDateRange(dateAndTimeTag, null)), dateTimeField.isNull())
                            : ExpressionUtils.and(combinedRange(dateTimeField, keys.getDateRange(dateAndTimeTag, null)), dateTimeField.isNotNull()));
        } else { 
            if (containsDateTag)
                predicates.and(matchUnknown 
                        ? ExpressionUtils.or(range(dateTimeField, keys.getDateRange(dateTag, null), FormatDate.DA), dateTimeField.isNull())
                                : ExpressionUtils.and(range(dateTimeField, keys.getDateRange(dateTag, null), FormatDate.DA), dateTimeField.isNotNull()));
            if (containsTimeTag)
            predicates.and(matchUnknown 
                    ? ExpressionUtils.or(range(dateTimeField, keys.getDateRange(timeTag, null), FormatDate.TM), dateTimeField.isNull())
                            : ExpressionUtils.and(range(dateTimeField, keys.getDateRange(timeTag, null), FormatDate.TM), dateTimeField.isNotNull()));
            
        }
        return predicates;
    }

    static Predicate rangeMatch(StringPath dateField, StringPath timeField, 
            int dateTag, int timeTag, long dateAndTimeTag, 
            Attributes keys, boolean combinedDatetimeMatching, boolean matchUnknown) {
        final boolean containsDateTag = keys.containsValue(dateTag);
        final boolean containsTimeTag = keys.containsValue(timeTag);
        if (!containsDateTag && !containsTimeTag)
            return null;
        
        BooleanBuilder predicates = new BooleanBuilder();
        if (containsDateTag && containsTimeTag && combinedDatetimeMatching) {
            predicates.and(matchUnknown(dateField, matchUnknown,
                    combinedRange(dateField, timeField, keys.getDateRange(dateAndTimeTag, null))));
        } else { 
            if (containsDateTag)
                predicates.and(matchUnknown(dateField, matchUnknown, 
                        range(dateField, keys.getDateRange(dateTag, null), FormatDate.DA)));
            if (containsTimeTag)
            	predicates.and(matchUnknown(timeField, matchUnknown, 
                        range(timeField, keys.getDateRange(timeTag, null), FormatDate.TM)));
            
        }
        return predicates;
    }

	private static Predicate matchUnknown(StringPath field, boolean matchUnknown, 
            Predicate predicate) {
        return matchUnknown 
            ? ExpressionUtils.or(predicate, field.eq("*"))
            : ExpressionUtils.and(predicate, field.ne("*"));
    }

    private static Predicate range(StringPath field, DateRange range, FormatDate dt) {
        Date startDate = range.getStartDate();
        Date endDate = range.getEndDate();
        if (startDate == null)
            return field.loe(dt.format(endDate));
        if (endDate == null)
            return field.goe(dt.format(startDate));
        return rangeInterval(field, startDate, endDate, dt, range);
    }

    private static Predicate range(DateTimePath<java.util.Date> dateTimeField, DateRange range, FormatDate dt) {
        Date startDate = range.getStartDate();
        Date endDate = range.getEndDate();
        if (startDate == null)
            return dateTimeField.loe(endDate);
        if (endDate == null)
            return dateTimeField.goe(startDate);
        return rangeInterval(dateTimeField, startDate, endDate, dt, range);
    }

    private static Predicate rangeInterval(DateTimePath<java.util.Date> field, Date startDate,
            Date endDate, FormatDate dt, DateRange range) {
        Calendar startCal = new GregorianCalendar();
        Calendar endCal = new GregorianCalendar();
        startCal.setTime(startDate);
        endCal.setTime(endDate);
        if(dt.equals(FormatDate.TM) && range.isStartDateExeedsEndDate()){
            Calendar midnightLow = new GregorianCalendar();
            midnightLow.setTime(startDate);
            midnightLow.set(Calendar.HOUR_OF_DAY, 23);
            midnightLow.set(Calendar.MINUTE, 59);
            midnightLow.set(Calendar.SECOND,59);
            midnightLow.set(Calendar.MILLISECOND,999);
            Calendar midnightHigh = new GregorianCalendar();
            midnightHigh.setTime(endDate);
            midnightHigh.set(Calendar.HOUR_OF_DAY, 0);
            midnightHigh.set(Calendar.MINUTE, 0);
            midnightHigh.set(Calendar.SECOND,0);
            midnightHigh.set(Calendar.MILLISECOND,0);
                return ExpressionUtils.or(field.between(startDate, midnightLow.getTime()),field.between(midnightHigh.getTime(), endDate));
        }
        else
        {

             return dt == FormatDate.DA? dateEqual(startCal, endCal)
                     ? dateEqual(field, startCal)
                     : field.between(startDate, endDate)
                     : timeEquals(startCal, endCal)
                     ? timeEqual(field, startCal)
                     : timeGreaterThanOrEqual(field, startCal).and(timeLessThanOrEqual(field, endCal));
        }
    }

    private static boolean timeEquals(Calendar time1, Calendar time2) {
        return time1.get(Calendar.HOUR_OF_DAY) == time2.get(Calendar.HOUR_OF_DAY)
                && time1.get(Calendar.MINUTE) == time2.get(Calendar.MINUTE)
                && time1.get(Calendar.HOUR_OF_DAY) == time2.get(Calendar.HOUR_OF_DAY)
                && time1.get(Calendar.MILLISECOND) == time2.get(Calendar.MILLISECOND);
    }

    private static Predicate rangeInterval(StringPath field, Date startDate,
            Date endDate, FormatDate dt, DateRange range) {
        String start = dt.format(startDate);
        String end = dt.format(endDate);
    	if(dt.equals(FormatDate.TM) && range.isStartDateExeedsEndDate()){
    		String midnightLow = "115959.999";
    		String midnightHigh = "000000.000";
    		return ExpressionUtils.or(field.between(start, midnightLow),field.between(midnightHigh, end));
    	}
    	else
    	{
    	     return end.equals(start)
    	             ? field.eq(start)
    	             : field.between(start, end);
    	}
    }

    private static Predicate combinedRange(DateTimePath<java.util.Date> dateTimeField, DateRange dateRange) {
        if (dateRange.getStartDate() == null)
            return combinedRangeEnd(dateTimeField, 
                    dateRange.getEndDate());
        if (dateRange.getEndDate() == null)
            return combinedRangeStart(dateTimeField, 
                    dateRange.getStartDate());
        return combinedRangeInterval(dateTimeField, 
                    dateRange.getStartDate(), dateRange.getEndDate());
    }

    private static Predicate combinedRange(StringPath dateField, StringPath timeField, DateRange dateRange) {
        if (dateRange.getStartDate() == null)
            return combinedRangeEnd(dateField, timeField, 
                    DateUtils.formatDA(null, dateRange.getEndDate()), 
                    DateUtils.formatTM(null, dateRange.getEndDate()));
        if (dateRange.getEndDate() == null)
            return combinedRangeStart(dateField, timeField, 
                    DateUtils.formatDA(null, dateRange.getStartDate()), 
                    DateUtils.formatTM(null, dateRange.getStartDate()));
        return combinedRangeInterval(dateField, timeField, 
                    dateRange.getStartDate(), dateRange.getEndDate());
    }

    private static Predicate combinedRangeInterval(DateTimePath<java.util.Date> dateTimeField,
            Date startDateTimeRange, Date endDateTimeRange) {
        Calendar startDateTimeRangeCal = new GregorianCalendar();
        startDateTimeRangeCal.setTime(startDateTimeRange);
        Calendar endDateTimeRangeCal = new GregorianCalendar();
        startDateTimeRangeCal.setTime(endDateTimeRange);
        
        return dateEqual(endDateTimeRangeCal, startDateTimeRangeCal)
            ? ExpressionUtils.allOf(dateEqual(dateTimeField, startDateTimeRangeCal), 
                    timeGreaterThanOrEqual(dateTimeField, startDateTimeRangeCal),
                    timeLessThanOrEqual(dateTimeField, endDateTimeRangeCal))
            : ExpressionUtils.and(
                    combinedRangeStart(dateTimeField, startDateTimeRange), 
                    combinedRangeEnd(dateTimeField, endDateTimeRange));
    }

    private static Predicate combinedRangeInterval(StringPath dateField,
            StringPath timeField, Date startDateRange, Date endDateRange) {
        String startTime = DateUtils.formatTM(null, startDateRange);
        String endTime = DateUtils.formatTM(null, endDateRange);
        String startDate = DateUtils.formatDA(null, startDateRange);
        String endDate = DateUtils.formatDA(null, endDateRange);
        return endDate.equals(startDate)
            ? ExpressionUtils.allOf(dateField.eq(startDate), 
                    timeField.goe(startTime), timeField.loe(endTime))
            : ExpressionUtils.and(
                    combinedRangeStart(dateField, timeField, startDate, startTime), 
                    combinedRangeEnd(dateField, timeField, endDate, endTime));
    }

    private static Predicate combinedRangeEnd(DateTimePath<java.util.Date> dateTimeField
            , Date endDateAndTime) {
        Calendar endDateTimeCal = new GregorianCalendar();
        endDateTimeCal.setTime(endDateAndTime);
        Predicate endDayTime = ExpressionUtils.and(
                dateEqual(dateTimeField, endDateTimeCal),
                timeLessThanOrEqual(dateTimeField, endDateTimeCal));
        Predicate endDayTimeUnknown = ExpressionUtils.and(
                dateEqual(dateTimeField, endDateTimeCal),
                timeUndefined(dateTimeField));
        Predicate endDayPrevious = 
                dateLessThan(dateTimeField, endDateTimeCal);

        return ExpressionUtils.anyOf(endDayTime, endDayTimeUnknown, endDayPrevious);
    }

    private static Predicate combinedRangeEnd(StringPath dateField,
            StringPath timeField, String endDate, String endTime) {
        Predicate endDayTime =
            ExpressionUtils.and(dateField.eq(endDate), timeField.loe(endTime));
        Predicate endDayTimeUnknown =
            ExpressionUtils.and(dateField.eq(endDate), timeField.eq("*"));
        Predicate endDayPrevious = dateField.lt(endDate);
        return ExpressionUtils.anyOf(endDayTime, endDayTimeUnknown, endDayPrevious);
    }

    private static Predicate combinedRangeStart(StringPath dateField,
            StringPath timeField, String startDate, String startTime) {
        Predicate startDayTime = 
            ExpressionUtils.and(dateField.eq(startDate), timeField.goe(startTime));
        Predicate startDayTimeUnknown = 
            ExpressionUtils.and(dateField.eq(startDate), timeField.eq("*"));
        Predicate startDayFollowing = dateField.gt(startDate);
        return ExpressionUtils.anyOf(startDayTime, startDayTimeUnknown, startDayFollowing);
    }

    private static Predicate combinedRangeStart(DateTimePath<java.util.Date> dateTimeField
            , Date startDateTime) {
        Calendar startDateTimeCal = new GregorianCalendar();
        startDateTimeCal.setTime(startDateTime);
        Predicate startDayTime = ExpressionUtils.and(
                        dateEqual(dateTimeField, startDateTimeCal),
                        timeGreaterThanOrEqual(dateTimeField, startDateTimeCal));
        Predicate startDayTimeUnknown = ExpressionUtils.and(
                dateEqual(dateTimeField, startDateTimeCal),
                timeUndefined(dateTimeField));
        ;
        Predicate startDayFollowing = 
                dateGreaterThan(dateTimeField, startDateTimeCal);

        return ExpressionUtils.anyOf(startDayTime, startDayTimeUnknown, startDayFollowing);

    }

    private static BooleanExpression dateGreaterThan(DateTimePath<java.util.Date> dateTimeField,
            Calendar startDateTimeCal) {
        return dateTimeField.year().gt(startDateTimeCal.get(Calendar.YEAR))
                .or(dateTimeField.year().eq(startDateTimeCal.get(Calendar.YEAR))
                        .and(dateTimeField.month().gt(startDateTimeCal.get(Calendar.MONTH) + 1))) //compensate for POSIX representation of month
                .or(dateTimeField.year().eq(startDateTimeCal.get(Calendar.YEAR))
                        .and(dateTimeField.month().eq(startDateTimeCal.get(Calendar.MONTH) + 1) // compensate for POSIX representation of month
                                .and(dateTimeField.dayOfMonth().gt(startDateTimeCal.get(Calendar.DAY_OF_MONTH)))));
    }

    private static BooleanExpression timeGreaterThanOrEqual(DateTimePath<java.util.Date> dateTimeField,
            Calendar startDateTimeCal) {
        return dateTimeField.hour().goe(startDateTimeCal.get(Calendar.HOUR_OF_DAY))
                .or(dateTimeField.hour().eq(startDateTimeCal.get(Calendar.HOUR_OF_DAY))
                        .and(dateTimeField.minute().goe(startDateTimeCal.get(Calendar.MINUTE))))
                .or(dateTimeField.hour().eq(startDateTimeCal.get(Calendar.HOUR_OF_DAY))
                        .and(dateTimeField.minute().eq(startDateTimeCal.get(Calendar.MINUTE))
                                .and(dateTimeField.second().goe(startDateTimeCal.get(Calendar.SECOND)))))
                .or(dateTimeField.hour().eq(startDateTimeCal.get(Calendar.HOUR_OF_DAY))
                        .and(dateTimeField.minute().eq(startDateTimeCal.get(Calendar.MINUTE))
                                .and(dateTimeField.second().eq(startDateTimeCal.get(Calendar.SECOND))
                                        .and(dateTimeField.milliSecond().goe(startDateTimeCal.get(Calendar.MILLISECOND))))));
    }

    private static BooleanExpression dateLessThan(DateTimePath<java.util.Date> dateTimeField, Calendar endDateTimeCal) {
        return dateTimeField.year().lt(endDateTimeCal.get(Calendar.YEAR))
                .or(dateTimeField.year().eq(endDateTimeCal.get(Calendar.YEAR))
                        .and(dateTimeField.month().lt(endDateTimeCal.get(Calendar.MONTH) + 1))) //compensate for POSIX representation of month
                .or(dateTimeField.year().eq(endDateTimeCal.get(Calendar.YEAR))
                        .and(dateTimeField.month().eq(endDateTimeCal.get(Calendar.MONTH) + 1) //compensate for POSIX representation of month
                                .and(dateTimeField.dayOfMonth().lt(endDateTimeCal.get(Calendar.DAY_OF_MONTH)))));
    }

    private static BooleanExpression timeUndefined(DateTimePath<java.util.Date> dateTimeField) {
        return dateTimeField.hour().eq(0).and(dateTimeField.minute().eq(0).and(dateTimeField.second().eq(0)));
    }

    private static BooleanExpression dateEqual(DateTimePath<java.util.Date> dateTimeField, Calendar dateCal) {
        return dateTimeField.year().eq(dateCal.get(Calendar.YEAR))
                .and(dateTimeField.month().eq(dateCal.get(Calendar.MONTH) + 1) //compensate for POSIX representation of month 
                        .and(dateTimeField.dayOfMonth().eq(dateCal.get(Calendar.DAY_OF_MONTH))));
    }

    private static BooleanExpression timeEqual(DateTimePath<java.util.Date> dateTimeField, Calendar timeCal) {
        return dateTimeField.hour().eq(timeCal.get(Calendar.HOUR_OF_DAY))
                .and(dateTimeField.minute().eq(timeCal.get(Calendar.MINUTE))
                        .and(dateTimeField.second().eq(timeCal.get(Calendar.SECOND))
                                .and(dateTimeField.milliSecond().eq(timeCal.get(Calendar.MILLISECOND)))));
    }
    private static boolean dateEqual(Calendar date1, Calendar date2) {
        return date1.get(Calendar.YEAR) == date2.get(Calendar.YEAR)
                && date1.get(Calendar.MONTH) == date2.get(Calendar.MONTH)
                && date1.get(Calendar.DAY_OF_MONTH) == date2.get(Calendar.DAY_OF_MONTH);
    }

    private static BooleanExpression timeLessThanOrEqual(DateTimePath<java.util.Date> dateTimeField,
            Calendar endDateTimeCal) {
        return dateTimeField.hour().loe(endDateTimeCal.get(Calendar.HOUR_OF_DAY))
                .or(dateTimeField.hour().eq(endDateTimeCal.get(Calendar.HOUR_OF_DAY))
                        .and(dateTimeField.minute().loe(endDateTimeCal.get(Calendar.MINUTE))))
                .or(dateTimeField.hour().eq(endDateTimeCal.get(Calendar.HOUR_OF_DAY))
                        .and(dateTimeField.minute().eq(endDateTimeCal.get(Calendar.MINUTE))
                                .and(dateTimeField.second().loe(endDateTimeCal.get(Calendar.SECOND)))))
                .or(dateTimeField.hour().eq(endDateTimeCal.get(Calendar.HOUR_OF_DAY))
                        .and(dateTimeField.minute().eq(endDateTimeCal.get(Calendar.MINUTE))
                                .and(dateTimeField.second().eq(endDateTimeCal.get(Calendar.SECOND))
                                        .and(dateTimeField.milliSecond().loe(endDateTimeCal.get(Calendar.MILLISECOND))))));
    }
}
