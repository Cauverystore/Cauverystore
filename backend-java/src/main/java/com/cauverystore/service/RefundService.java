package com.cauverystore.service;

import com.cauverystore.entities.Refund;

import java.util.List;

public interface RefundService {
    List<Refund> getAllRefunds(String status);
    Refund updateRefundStatus(Long id, String status);
}
