package com.payment.payUPayment.controller;


import com.payment.payUPayment.model.Enrollment;
import com.payment.payUPayment.service.EmailService;
import com.payment.payUPayment.service.EnrollmentService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Author:: shuvra
 * Version:: 1.0
 * DATE:: 26.08.2024
 */

@RestController
@RequestMapping("/api/payments")
@CrossOrigin("*")
public class PayUPayment {

    @Value("${payu.api.url}")
    private String apiUrl;

    @Value("${payu.merchant.key}")
    private String merchantKey;

    @Value("${payu.merchant.salt}")
    private String merchantSalt;

    @Value("${payu.success.url}")
    private String successUrl;

    @Value("${payu.failure.url}")
    private String failureUrl;

    @Autowired
    private EnrollmentService enrollmentService;


    @Autowired
    private EmailService emailService;


    /**
     *
     * @param payload
     * @return redirect to the payment page
     */
    @PostMapping(value = "/create-checkout-session", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createCheckoutSession(@RequestBody Map<String, Object> payload) {
//        Double price = payload.get("price") != null ? Double.valueOf(payload.get("price").toString()) : 0.0;
        String name = payload.get("recipientName") != null ? payload.get("recipientName").toString() : "Unknown";
        String email = payload.get("recipientEmail") != null ? payload.get("recipientEmail").toString() : "no-email@example.com";
        String phone = payload.get("recipientPhone") != null ? payload.get("recipientPhone").toString() : "0000000000";
        Double price = 500.00;
        String productInfo = "Course Payment";

        String txnId = UUID.randomUUID().toString().replace("-", "");

        Map<String, String> paymentRequest = new HashMap<>();
        paymentRequest.put("key", merchantKey);
        paymentRequest.put("txnid", txnId);
        paymentRequest.put("amount", String.format("%.2f", price));
        paymentRequest.put("productinfo", productInfo);
        paymentRequest.put("firstname", name);
        paymentRequest.put("email", email);
        paymentRequest.put("phone", phone);
        paymentRequest.put("surl", successUrl);
        paymentRequest.put("furl", failureUrl);
        paymentRequest.put("service_provider", "payu_paisa");

        // Generate the hash for the request
        String hash = generateHashString(paymentRequest);
        paymentRequest.put("hash", hash);

        System.out.println("Payment Request: " + paymentRequest); // Debugging output

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String encodedRequest = paymentRequest.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            HttpEntity<String> entity = new HttpEntity<>(encodedRequest, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            System.out.println("PayU Response: " + response); // Debugging output

            if (response.getStatusCode() == HttpStatus.FOUND || response.getStatusCode() == HttpStatus.OK) {
                URI paymentUrl = response.getHeaders().getLocation();
                if (paymentUrl != null) {
                    Map<String, String> responseData = new HashMap<>();
                    responseData.put("payment_url", paymentUrl.toString());
                    return ResponseEntity.ok(responseData);
                } else {
                    System.err.println("No redirect URL found in response");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Collections.singletonMap("error", "No redirect URL provided by payment gateway."));
                }
            } else {
                System.err.println("Unexpected response status: " + response.getStatusCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Collections.singletonMap("error", "Unexpected response from payment gateway."));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "An error occurred while processing the payment."));
        }
    }

    /**
     *
     * @param params
     * @return Success save data in DB and send mail.
     */
    @GetMapping(value = "/ots-response/success")
    public ResponseEntity<String> paymentSuccess(@RequestParam Map<String, String> params) {
        try {
            // Extract PayU response parameters
            String status = params.get("status");
            String txnId = params.get("txnid");
            String name = params.get("firstname");
            String email = params.get("email");
            String amountStr = params.get("amount");
            String courseIdStr = params.get("udf1");

            if ("success".equalsIgnoreCase(status) && txnId != null) {
                Double enrollPrice = Double.valueOf(amountStr);
                Long courseId = Long.valueOf(courseIdStr);

                // Create and save the enrollment
                Enrollment newEnrollment = new Enrollment();
                newEnrollment.setRecipientName(name);
                newEnrollment.setRecipientEmail(email);
                newEnrollment.setSendDate(new Date().toString());
                newEnrollment.setEnrollPrice(enrollPrice);

                enrollmentService.saveEnrollment(newEnrollment);

//                // Send a confirmation email
//                String subject = "Enrollment Confirmation";
//                String emailText = String.format(
//                        "Dear %s,\n\nYou have successfully enrolled in the course %s.\n\nCourse Details:\nTitle: %s\nDescription: %s\nPrice: %.2f\nDate: %s\n\nThank you!",
//                        name, course.getTitle(), course.getTitle(), course.getDescription(), enrollPrice, new Date().toString());
//                emailService.sendSimpleEmail(email, subject, emailText);

                return ResponseEntity.ok("Payment successful and enrollment saved");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment failed");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process the payment response");
        }
    }

    /**
     *
     * @return failure
     */
    @GetMapping("/ots-response/failure")
    public ResponseEntity<String> paymentFailure() {
        // You could also include more detailed information here if needed
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Payment failed. Please try again later.");
    }


    /**
     *
     * @param params
     * @return hash of all data
     */
    private String generateHashString(Map<String, String> params) {
        StringBuilder hashString = new StringBuilder();
        hashString.append(params.get("key")).append("|");
        hashString.append(params.get("txnid")).append("|");
        hashString.append(params.get("amount")).append("|");
        hashString.append(params.get("productinfo")).append("|");
        hashString.append(params.get("firstname")).append("|");
        hashString.append(params.get("email")).append("|");
        hashString.append(params.getOrDefault("udf1", "")).append("|");
        hashString.append(params.getOrDefault("udf2", "")).append("|");
        hashString.append(params.getOrDefault("udf3", "")).append("|");
        hashString.append(params.getOrDefault("udf4", "")).append("|");
        hashString.append(params.getOrDefault("udf5", "")).append("||||||");
        hashString.append(merchantSalt);

        return DigestUtils.sha512Hex(hashString.toString());
    }

}

