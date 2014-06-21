-- Map G2 GSI names to Ignition Tags
-- Columns are GSI name, tag path
insert into TagMap values ('A-BALER-TEMP-LAB-DATA','[]LabData/A_BALER_TEMP/value','DOUBLE');
insert into TagMap values ('AB-BALER-TEMP-LAB-DATA','[]LabData/AB_BALER_TEMP/value','DOUBLE');
insert into TagMap values ('AB-BALER-VOL-LAB-DATA','[]LabData/AB_BALER_VOL/value','DOUBLE');
insert into TagMap values ('B-BALER-TEMP-LAB-DATA','[]LabData/B_BALER_TEMP/value','DOUBLE');
insert into TagMap values ('CA-LAB-DATA','[]LabData/CA/value','DOUBLE');
insert into TagMap values ('C2-LAB-DATA','[]LabData/C2/value','DOUBLE');
insert into TagMap values ('C2-LAB-DATA-FOR-R1-NLC','[]LabData/C2_R1_NLC','DOUBLE');
insert into TagMap values ('C3CONV_LOW_LIMIT','[]Tags/C3_CONVERSION/lowLimit','DOUBLE');
insert into TagMap values ('C3_CONVERSION','[]Tags/C3_CONVERSION/value','DOUBLE');
insert into TagMap values ('C3-PURITY-HI','[]Tags/C3_PURITY_HI','DOUBLE');
insert into TagMap values ('C6_RX_FEED-VNB','[]Tags/C6_RX_IN_FEED','DOUBLE');
insert into TagMap values ('C9-IN-CRUMB','[]Tags/C9_IN_CRUMB','DOUBLE');
insert into TagMap values ('C9-LAB-DATA','[]LabData/C9/value','DOUBLE');
insert into TagMap values ('C9-SPEC-LIMIT-IN-FEED','[]LabData/C9_SPEC_LIMIT_IN_FEED/value','DOUBLE');
insert into TagMap values ('C101-ETHYLENE','[]Tags/C101_ETHYLENE','DOUBLE');
insert into TagMap values ('CAT_EFFICIENCY','[]Tags/CAT_EFFICIENCY/value','DOUBLE');
insert into TagMap values ('CATEFF_HIGH_LIMIT','[]Tags/CAT_EFFICIENCY/highLimit','DOUBLE');
insert into TagMap values ('CAT_EFF_HIGH_LIMIT','[]Tags/CAT_EFFICIENCY/highLimit','DOUBLE');
insert into TagMap values ('CAT_PREMIX_TEMP','[]Tags/CAT_PREMIX_TEMP/value','DOUBLE');
insert into TagMap values ('CD-BALER-TEMP-LAB-DATA','[]Tags/CD_BALER_TEMP/value','DOUBLE');
insert into TagMap values ('CD-BALER-VOL-LAB-DATA','[]Tags/CD_BALER_VOL/value','DOUBLE');
insert into TagMap values ('CD-BALER-VOL-ftnir-DATA','[]Tags/CD_BALER_VOL_FTNIR_DATA/value','DOUBLE');
insert into TagMap values ('CNTR_AVG_TPR_TIP_HT','[]Tags/CNTR_AVG_TPR_TIP_HT/value','DOUBLE');
insert into TagMap values ('CNTR_AVG_TPR_TIP_HT_MAX_DEADBAND','[]Tags/CNTR_AVG_TPR_TIP_HT/maxDeadband','DOUBLE');
insert into TagMap values ('CNTR_AVG_TPR_TIP_HT_TARGET','[]Tags/CNTR_AVG_TPR_TIP_HT/target','DOUBLE');
insert into TagMap values ('CRX-BLOCK-POLYMER-FLAG','[]Tags/CRX_BLOCK_POLYMER_FLAG/value','DOUBLE');
insert into TagMap values ('CRX_HB-3','[]Tags/CRX_HB_3','DOUBLE');
insert into TagMap values ('E-BALER-VOL-LAB-DATA','[]LabData/E_BALER_VOL/value','DOUBLE');
insert into TagMap values ('FRNT_AVG_C2','[]Tags/AVG_C2/value','DOUBLE');
insert into TagMap values ('FRNT_AVG_C2_HIGH_LIMIT','[]Tags/AVG_C2/highLimit','DOUBLE');
insert into TagMap values ('FRNT_FEED_DIFF','[]Tags/FRNT_FEED_DIFF/value','DOUBLE');
insert into TagMap values ('FRNT_LNGTH','[]Tags/FRNT_LNGTH/value','DOUBLE');
insert into TagMap values ('FRNT_LNGTH_HIGH_LIMIT','[]Tags/FRNT_LNGTH/highLimit','DOUBLE');
insert into TagMap values ('FRNT_LNGTH_LOW_LIMIT','[]Tags/FRNT_LNGTH/lowLimit','DOUBLE');
insert into TagMap values ('FRNT_LNGTH_TARGET','[]Tags/FRNT_LNGTH/target','DOUBLE');
insert into TagMap values ('FRNT_SDSTRM_MAX_DIFF','[]Tags/FRNT_SDSTRM/maxDiff','DOUBLE');
insert into TagMap values ('FRNT_TPR_TIP_HT_HIGH_LIMIT','[]Tags/FRNT_TPR_TIP_HT/highLimit','DOUBLE');
insert into TagMap values ('FRNT_TPR_TIP_HT_DIFF','[]Tags/FRNT_TPR_TIP_HT/diff','DOUBLE');
insert into TagMap values ('FRNT_TPR_TIP_HT_MAX_DIFF','[]Tags/FRNT_TPR_TIP_HT/maxDiff','DOUBLE');
insert into TagMap values ('MAX_CNTR_TPR_TIP_DELTA_FM_AVG','[]Tags/MAX_CNTR_TPR_TIP_DELTA_FM_AVG/value','DOUBLE');
insert into TagMap values ('MIXTEE_IN_USE_0_EAST_1_WEST','[]Tags/MIXTEE_IN_USE_0_EAST_1_WEST/value','DOUBLE');
insert into TagMap values ('MOONEY-LAB-DATA','[]LabData/MOONEY/value','DOUBLE');
insert into TagMap values ('MOONEY_RESET_TIME_FOR_SF-3','[]Tags/MOONEY_RESET_TIME_FOR_SF_3/value','DOUBLE');
insert into TagMap values ('PREMIX_TEMP_HIGH_LIMIT','[]Tags/PREMIX_TEMP/highLImit','DOUBLE');
insert into TagMap values ('RLA3-CURRENT-GRADE','[]Tags/RLA3-CURRENT-GRADE/value','DOUBLE');
insert into TagMap values ('RX_CONFIGURATION','[]Tags/RX_CONFIGURATION/value','DOUBLE');
insert into TagMap values ('SDSTRM-C3C2-RATIO','[]Tags/SDSTRM_C3C2_RATIO/value','DOUBLE');
insert into TagMap values ('SDSTRM_C3-TO-C2_RATIO_HIGH_LIMIT','[]Tags/SDSTRM_C3-TO-C2_RATIO/highLimit','DOUBLE');
insert into TagMap values ('SDSTRM_C3-TO-C2_RATIO_LOW_LIMIT','[]Tags/SDSTRM_C3-TO-C2_RATIO/lowLimit','DOUBLE');
insert into TagMap values ('SS1_TAPER_TIP_HEIGHT','[]Tags/SS1_TAPER_TIP_HEIGHT/value','DOUBLE');
insert into TagMap values ('SS2_TAPER_TIP_HEIGHT','[]Tags/SS2_TAPER_TIP_HEIGHT/value','DOUBLE');
-- UDT paths
insert into TagMap values ('[the target of A-BALER-TEMP-LAB-DATA]','[]LabData/A_BALER_TEMP/target','DOUBLE');
insert into TagMap values ('[the target of AB-BALER-TEMP-LAB-DATA]','[]LabData/AB_BALER_TEMP/target','DOUBLE');
insert into TagMap values ('[the target of B-BALER-TEMP-LAB-DATA]','[]LabData/B_BALER_TEMP/target','DOUBLE');
insert into TagMap values ('[the standard-deviation of C2-LAB-DATA]','[]LabData/C2/standardDeviation','DOUBLE');
insert into TagMap values ('[the target of C2-LAB-DATA]','[]LabData/C2/target','DOUBLE');
insert into TagMap values ('[the bad-value of C3_CONVERSION]','[]Tags/C3_CONVERSION/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of CAT_EFFICIENCY]','[]Tags/CAT_EFFICIENCY/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of CAT_PREMIX_TEMP]','[]Tags/CAT_PREMIX_TEMP/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of CNTR_AVG_TPR_TIP_HT]','[]Tags/CNTR_AVG_TPR_TIP_HT/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of CRX-BLOCK-POLYMER-FLAG]','[]Tags/CRX_BLOCK_POLYMER_FLAG/badValue','DOUBLE');
insert into TagMap values ('[the target of E-BALER-VOL-lab-DATA]','[]LabData/E_BALER_VOL/target','DOUBLE');
insert into TagMap values ('[the target of E-BALER-VOL-ftnir-DATA]','[]Tags/E_BALER_VOL_FTNIR_DATA/target','DOUBLE');
insert into TagMap values ('[the bad-value of FRNT_AVG_C2]','[]Tags/FRNT_AVG_C2/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of FRNT_FEED_DIFF]','[]Tags/FRNT_FEED_DIFF/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of FRNT_LNGTH]','[]Tags/FRNT_LNGTH/badValue','DOUBLE');
insert into TagMap values ('[the time-of-most-recent-recommendation-implementation of frnt_short_use_temp-gda]','[]Tags/FRNT_SHORT_USE_TEMP_GDA/implementationTime','INTEGER');
insert into TagMap values ('[the bad-value of FRNT_TPR_TIP_HT_DIFF]','[]Tags/FRNT_TPR_TIP_HT_DIFF/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of MAX_CNTR_TPR_TIP_DELTA_FM_AVG]','[]Tags/MAX_CNTR_TPR_TIP_DELTA_FM_AVG/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of MIXTEE_IN_USE_0_EAST_1_WEST]','[]Tags/MIXTEE_IN_USE_0_EAST_1_WEST/badValue','DOUBLE');
insert into TagMap values ('[the standard-deviation of MOONEY-LAB-DATA]','[]LabData/MOONEY/standardDeviation','DOUBLE');
insert into TagMap values ('[the unix-sample-time of mooney-lab-data]','[]LabData/MOONEY/sampleTime','INTEGER');
insert into TagMap values ('[the target of MOONEY-LAB-DATA]','[]LabData/MOONEY/target','DOUBLE');
insert into TagMap values ('[the bad-value of MOONEY_RESET_TIME_FOR_SF-3]','[]Tags/MOONEY_RESET_TIME_FOR_SF_3/badValue','DOUBLE');
insert into TagMap values ('[the time-of-most-recent-grade-change of rla3-run-hours]','[]Tags/RLA3_RUN_HOURS/gradeChangeTime','INTEGER');
insert into TagMap values ('[the bad-value of RLA3-CURRENT-GRADE]','[]Tags/RLA3_CURRENT_GRADE/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of RX_CONFIGURATION]','[]Tags/RX_CONFIGURATION/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of RX_CONFIGURATION ]','[]Tags/RX_CONFIGURATION/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of SDSTRM-C3C2-RATIO]','[]Tags/SDSTRM_C3C2_RATIO/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of SS1_TAPER_TIP_HEIGHT]','[]Tags/SS1_TAPER_TIP_HEIGHT/badValue','DOUBLE');
insert into TagMap values ('[the bad-value of SS2_TAPER_TIP_HEIGHT]','[]Tags/SS2_TAPER_TIP_HEIGHT/badValue','DOUBLE');