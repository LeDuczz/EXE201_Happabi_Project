package com.minduc.happabi.service.ocr;

import com.minduc.happabi.dto.response.nurse.CccdOcrExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ICccdOcrService {

    CccdOcrExtractionResponse extractFrontSide(MultipartFile frontImage);

}
