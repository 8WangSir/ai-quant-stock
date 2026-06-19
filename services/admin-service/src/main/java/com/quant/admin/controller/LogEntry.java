package com.quant.admin.controller;

import lombok.Data;

@Data
public class LogEntry {
    private String service;
    private String level;
    private String raw;
    private long timestamp;
}
