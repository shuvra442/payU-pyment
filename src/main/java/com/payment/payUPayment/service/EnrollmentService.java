package com.payment.payUPayment.service;

import com.payment.payUPayment.model.Enrollment;
import com.payment.payUPayment.repository.EnrollmentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnrollmentService {

    @Autowired
    private EnrollmentRepo enrollmentRepo;


    public List<Enrollment> getAllEnrollment(){
        return enrollmentRepo.findAll();
    }


    public Enrollment saveEnrollment(Enrollment enrollment) {
        return enrollmentRepo.save(enrollment);
    }
}
