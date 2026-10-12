package com.equinecare.service;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    public void sendToTrainer(Integer trainerId, String message) {
        System.out.println("[Gửi tới HLV ID=" + trainerId + "]: " + message);
    }

    public void sendToOwner(Integer ownerId, String message) {
        System.out.println("[Gửi tới Chủ ngựa ID=" + ownerId + "]: " + message);
    }

    public void sendToGroom(Integer groomId, String message) {
        System.out.println("[Gửi tới Groom ID=" + groomId + "]: " + message);
    }
}