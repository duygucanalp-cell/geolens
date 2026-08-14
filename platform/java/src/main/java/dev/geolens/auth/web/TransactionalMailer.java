package dev.geolens.auth.web;

/** İşlemsel e-posta gönderici — Go {@code emailSender} karşılığı (delivery.Service SendGrid). */
@FunctionalInterface
public interface TransactionalMailer {
    void sendEmail(String to, String subject, String htmlContent);
}