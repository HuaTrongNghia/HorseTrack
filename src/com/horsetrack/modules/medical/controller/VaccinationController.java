
import java.util.List;

/** Lịch tiêm phòng / tẩy giun (bảng Vaccinations) */
@RestController
@RequestMapping({"/api/v1/vaccinations", "/api/vaccinations"})
@RequiredArgsConstructor
public class VaccinationController {

    private final VaccinationService vaccinationService;

    // tab: due (quá hạn / đến hạn trong 7 ngày), upcoming, done, bỏ trống = tất cả; lọc theo quyền xem ngựa
   
    // Bác sĩ tạo lịch tiêm / tẩy giun cho nhiều ngựa
 
