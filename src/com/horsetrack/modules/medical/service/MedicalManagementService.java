

  



    /**
     * Thêm thuốc khi phác đồ đang điều trị (không tạo phiên bản mới): cộng vào dòng thuốc của giai đoạn (cùng thuốc thì tăng số lượng),
     * kho giữ ngay phần bổ sung, chủ ngựa nhận hóa đơn thuốc bổ sung; kho cấp phần bổ sung sau khi chủ ngựa thanh toán (trừ ca cấp cứu).
     */
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO addItemDuringTreatment(Integer protocolId, ProtocolItemAddRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!"IN_TREATMENT".equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ thêm thuốc khi phác đồ đang điều trị (IN_TREATMENT). Hiện tại: " + protocol.getState()
                    + ". Phác đồ chưa cấp thuốc thì tạo phiên bản mới.");
        }
        ProtocolPhase phase = protocol.getPhases().stream()
                .filter(ph -> ph.getPhaseNo().equals(request.getPhaseNo()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " không có giai đoạn " + request.getPhaseNo() + "."));
        LocalDate phaseEnd = phase.getStartDate().plusDays(Math.max(1, phase.getDurationDays()) - 1L);
        if (phaseEnd.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Giai đoạn " + phase.getPhaseNo() + " đã kết thúc ngày " + phaseEnd + ", không thêm thuốc được.");
        }
        InventoryItem item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thuốc/vật tư với ID: " + request.getItemId()));
        if (!com.horsetrack.equinecare.modules.inventory.service.ItemCategories.isMedical(item.getCategory())) {
            throw new IllegalArgumentException("'" + item.getItemName() + "' (nhóm " + item.getCategory()
                    + ") không thuộc danh mục thuốc. Chỉ kê được Thuốc, Vaccine hoặc Vật tư y tế.");
        }
        double qty = BigDecimal.valueOf(request.getQuantity()).setScale(2, RoundingMode.HALF_UP).doubleValue();
        String reason = request.getReason().trim();
        String dosage = StringUtils.hasText(request.getDosage()) ? request.getDosage().trim() : null;

        // Mỗi giai đoạn chỉ có một dòng cho mỗi thuốc (UQ_PI_phase_item): đã có thì tăng số lượng
        ProtocolItem line = phase.getItems().stream()
                .filter(it -> it.getItem().getItemId().equals(item.getItemId()))
                .findFirst()
                .orElse(null);
        if (line == null) {
            line = ProtocolItem.builder()
                    .item(item)
                    .quantity(qty)
                    .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                    .dosage(dosage)
                    .build();
            phase.addItem(line);
        } else {
            line.setQuantity(BigDecimal.valueOf(line.getQuantity() + qty).setScale(2, RoundingMode.HALF_UP).doubleValue());
            if (dosage != null) {
                line.setDosage(dosage);
            }
        }
        protocolRepository.saveAndFlush(protocol);

        String qtyText = BigDecimal.valueOf(qty).stripTrailingZeros().toPlainString() + " " + item.getUnit();
        holdRepository.save(com.horsetrack.equinecare.modules.inventory.entity.StockHold.builder()
                .protocolId(protocol.getProtocolId())
                .item(item)
                .qtyNeeded(qty)
                .qtyAvailable(0d)
                .qtyHeld(0d)
                .status("PENDING_REVIEW")
                .checkedAt(LocalDateTime.now())
                .build());

        noteRepository.save(ProtocolProgressNote.builder()
                .protocol(protocol)
                .writtenBy(currentUser())
                .writtenAt(LocalDateTime.now())
                .content("Chờ kho duyệt thuốc GĐ " + phase.getPhaseNo() + ": " + item.getItemName() + " x " + qtyText
                        + (dosage != null ? " (" + dosage + ")" : "") + ". Lý do: " + reason)
                .build());
        auditTrail.record("ADD_PROTOCOL_ITEM", "Protocol_Items", null,
                "phác đồ " + protocol.getProtocolCode() + " | GĐ " + phase.getPhaseNo() + " | " + item.getItemName() + " x " + qtyText
                        + " | chờ kho duyệt | " + reason);
        return mapper.toDTO(protocol, true);
    }

    /**
     * Bác sĩ kết thúc giai đoạn trước ngày kết thúc đã định.
     * Giai đoạn được rút còn tới hôm nay và ghi nhận hoàn thành.
     */
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO finishPhaseEarly(Integer protocolId, Integer phaseNo, ProtocolPhaseDecisionDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!"IN_TREATMENT".equals(protocol.getState())
                && !com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ kết thúc sớm giai đoạn khi phác đồ đang điều trị hoặc đang chờ thanh toán. Hiện tại: " + protocol.getState() + ".");
        }
        ProtocolPhase phase = phase(protocol, phaseNo);
        Set<Integer> finished = finishedPhases(protocolId);
        if (finished.contains(phaseNo)) {
            throw new IllegalArgumentException("Giai đoạn " + phaseNo + " (" + phase.getPhaseName() + ") đã hoàn thành.");
        }
        for (ProtocolPhase earlier : protocol.getPhases()) {
            if (earlier.getPhaseNo() != null && earlier.getPhaseNo() < phaseNo && !finished.contains(earlier.getPhaseNo())) {
                throw new IllegalArgumentException("Phải hoàn thành giai đoạn " + earlier.getPhaseNo() + " (" + earlier.getPhaseName() + ") trước.");
            }
        }
        LocalDate today = LocalDate.now();
        if (phase.getStartDate().isAfter(today)) {
            throw new IllegalArgumentException("Giai đoạn " + phaseNo + " chưa bắt đầu (ngày " + phase.getStartDate() + "). Hãy bắt đầu sớm giai đoạn này trước.");
        }
        phaseBilling.assertCanFinishPhase(protocol, phaseNo);
        LocalDate plannedEnd = phase.getStartDate().plusDays(Math.max(1, phase.getDurationDays()) - 1L);
        if (plannedEnd.isAfter(today)) {
            phase.setDurationDays((int) ChronoUnit.DAYS.between(phase.getStartDate(), today) + 1);
        }
        String note = decisionNote(request);
        String content = "Hoàn thành giai đoạn " + phaseNo + " (kết thúc sớm)";
        if (!note.isEmpty()) {
            content = content + ". " + note;
        }
        noteRepository.save(ProtocolProgressNote.builder()
                .protocol(protocol)
                .writtenBy(currentUser())
                .writtenAt(LocalDateTime.now())
                .content(content)
                .build());
        protocolRepository.save(protocol);
        auditTrail.record("FINISH_PHASE_EARLY", "Protocol_Phases", "phase_no=" + phaseNo,
                "kết thúc sớm giai đoạn " + phaseNo + " (" + phase.getPhaseName() + ") | phác đồ " + protocol.getProtocolCode()
                        + (note.isEmpty() ? "" : " | " + note));
        return mapper.toDTO(protocol, true);
    }

    /**
     * Bác sĩ hoàn thành giai đoạn đang điều trị.
     * Có giai đoạn sau và chủ ngựa đã trả: chuyển sang giai đoạn đó.
     * Có giai đoạn sau nhưng chưa trả: chờ thanh toán, không thu các giai đoạn xa hơn.
     * Hết giai đoạn, hoặc bác sĩ ngưng vì lý do chuyên môn: đóng phác đồ, hủy hoặc hoàn hóa đơn phần chưa làm.
     */
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO completePhase(Integer protocolId, Integer phaseNo, ProtocolPhaseDecisionDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!"IN_TREATMENT".equals(protocol.getState())
                && !com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ hoàn thành giai đoạn khi phác đồ đang điều trị hoặc đang chờ thanh toán. Hiện tại: " + protocol.getState() + ".");
        }
        ProtocolPhase phase = phase(protocol, phaseNo);
        boolean continueTreatment = request == null || request.getContinueTreatment() == null || Boolean.TRUE.equals(request.getContinueTreatment());
        String note = decisionNote(request);
        if (!continueTreatment && note.isEmpty()) {
            throw new IllegalArgumentException("Cần ghi lý do chuyên môn khi ngưng phác đồ sau giai đoạn " + phaseNo + ".");
        }
        Set<Integer> finished = finishedPhases(protocolId);
        if (finished.contains(phaseNo)) {
            throw new IllegalArgumentException("Giai đoạn " + phaseNo + " (" + phase.getPhaseName() + ") đã hoàn thành.");
        }
        for (ProtocolPhase earlier : protocol.getPhases()) {
            if (earlier.getPhaseNo() != null && earlier.getPhaseNo() < phaseNo && !finished.contains(earlier.getPhaseNo())) {
                throw new IllegalArgumentException("Phải hoàn thành giai đoạn " + earlier.getPhaseNo() + " (" + earlier.getPhaseName() + ") trước.");
            }
        }
        phaseBilling.assertCanFinishPhase(protocol, phaseNo);
        LocalDate today = LocalDate.now();
        boolean early = shortenPhaseToToday(phase, today);
        String content = "Hoàn thành giai đoạn " + phaseNo + (early ? " (kết thúc sớm)" : "");
        if (!note.isEmpty()) {
            content = content + ". " + note;
        }
        writeNote(protocol, cap(content));
        protocolRepository.saveAndFlush(protocol);

        ProtocolPhase next = null;
        for (ProtocolPhase candidate : protocol.getPhases()) {
            if (candidate.getPhaseNo() != null && candidate.getPhaseNo() > phaseNo && (next == null || candidate.getPhaseNo() < next.getPhaseNo())) {
                next = candidate;
            }
        }
        if (!continueTreatment || next == null) {
            String outcome = !continueTreatment
                    ? "Ngưng sau giai đoạn " + phaseNo + ": " + note
                    : (note.isEmpty() ? "Hoàn thành giai đoạn " + phaseNo : note);
            closeAfterPhase(protocol, phaseNo, cap(outcome));
            auditTrail.record("COMPLETE_PHASE", "Protocol_Phases", "phase_no=" + phaseNo,
                    "hoàn thành giai đoạn " + phaseNo + " | kết thúc phác đồ " + protocol.getProtocolCode()
                            + (continueTreatment ? "" : " | ngưng | " + note));
            return mapper.toDTO(protocol, true);
        }
        if (!phaseBilling.isPhaseSettled(protocol, next.getPhaseNo())
                && phaseBilling.findPhaseInvoice(protocol.getProtocolId(), next.getPhaseNo())
                .filter(inv -> "REFUNDED".equals(inv.getPaymentStatus())).isPresent()) {
            throw new IllegalArgumentException("Hóa đơn giai đoạn " + next.getPhaseNo() + " (" + next.getPhaseName()
                    + ") đã được hoàn tiền. Không thể chuyển sang giai đoạn này.");
        }
        if (phaseBilling.isPhaseSettled(protocol, next.getPhaseNo())) {
            LocalDate oldStart = next.getStartDate();
            boolean pulled = oldStart.isAfter(today);
            if (pulled) {
                next.setStartDate(today);
            }
            if (com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())) {
                protocol.setState("IN_TREATMENT");
            }
            String start = pulled
                    ? "Bắt đầu sớm giai đoạn " + next.getPhaseNo() + " (từ " + oldStart + " chuyển thành " + today + ")"
                    : "Bắt đầu giai đoạn " + next.getPhaseNo();
            writeNote(protocol, start);
            protocolRepository.saveAndFlush(protocol);
            phaseBilling.evaluate(protocol, today);
            auditTrail.record("COMPLETE_PHASE", "Protocol_Phases", "phase_no=" + phaseNo,
                    "hoàn thành giai đoạn " + phaseNo + " | bắt đầu giai đoạn " + next.getPhaseNo() + " | phác đồ " + protocol.getProtocolCode());
            return mapper.toDTO(protocol, true);
        }
        if (next.getStartDate().isAfter(today)) {
            phaseBilling.bringPhaseDueForward(protocol, next.getPhaseNo(), today);
        }
        writeNote(protocol, "Chờ chủ ngựa thanh toán giai đoạn " + next.getPhaseNo() + " (" + next.getPhaseName() + ") trước khi bắt đầu.");
        phaseBilling.holdForUnpaidNextPhase(protocol, next);
        auditTrail.record("COMPLETE_PHASE", "Protocol_Phases", "phase_no=" + phaseNo,
                "hoàn thành giai đoạn " + phaseNo + " | chờ thanh toán giai đoạn " + next.getPhaseNo() + " | phác đồ " + protocol.getProtocolCode());
        return mapper.toDTO(protocol, true);
    }

    /** Bác sĩ cho giai đoạn sau bắt đầu. Giai đoạn trước phải đã xong và giai đoạn này đã thanh toán. Không yêu cầu kho đã cấp thuốc. */
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO startPhaseEarly(Integer protocolId, Integer phaseNo, ProtocolPhaseDecisionDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!Set.of("VET_CONFIRMED", "READY_FOR_DISPENSING", "IN_TREATMENT",
                com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD).contains(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ bắt đầu giai đoạn khi phác đồ đã được xác nhận. Hiện tại: " + protocol.getState() + ".");
        }
        ProtocolPhase phase = phase(protocol, phaseNo);
        LocalDate today = LocalDate.now();
        if (phaseBilling.isPhaseStarted(protocolId, phaseNo)) {
            throw new IllegalArgumentException("Giai đoạn " + phaseNo + " (" + phase.getPhaseName() + ") đã bắt đầu.");
        }
        if (phaseNo > 1) {
            Set<Integer> finished = finishedPhases(protocolId);
            for (ProtocolPhase earlier : protocol.getPhases()) {
                if (earlier.getPhaseNo() != null && earlier.getPhaseNo() < phaseNo && !finished.contains(earlier.getPhaseNo())) {
                    throw new IllegalArgumentException("Hãy kết thúc giai đoạn " + earlier.getPhaseNo() + " (" + earlier.getPhaseName() + ") trước khi bắt đầu giai đoạn " + phaseNo + ".");
                }
            }
        } else if (!phase.getStartDate().isAfter(today)
                && ("IN_TREATMENT".equals(protocol.getState())
                || com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState()))) {
            throw new IllegalArgumentException("Giai đoạn 1 đã bắt đầu từ " + phase.getStartDate() + ".");
        }
        if (!phaseBilling.isPhaseSettled(protocol, phaseNo)) {
            throw new IllegalArgumentException("Chủ ngựa chưa thanh toán giai đoạn " + phaseNo + " (" + phase.getPhaseName() + "). Chưa thể bắt đầu giai đoạn này.");
        }
        LocalDate oldStart = phase.getStartDate();
        boolean early = oldStart.isAfter(today);
        if (early) {
            phase.setStartDate(today);
            phaseBilling.bringPhaseDueForward(protocol, phaseNo, today);
        }
        if (com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())) {
            protocol.setState("IN_TREATMENT");
        }
        protocolRepository.saveAndFlush(protocol);
        String note = decisionNote(request);
        String content = early
                ? "Bắt đầu sớm giai đoạn " + phaseNo + " (từ " + oldStart + " chuyển thành " + today + ")"
                : "Bắt đầu giai đoạn " + phaseNo;
        if (!note.isEmpty()) {
            content = content + ". " + note;
        }
        writeNote(protocol, cap(content));
        phaseBilling.evaluate(protocol, today);
        auditTrail.record("START_PHASE_EARLY", "Protocol_Phases", "start_date=" + oldStart,
                (early ? "bắt đầu sớm giai đoạn " : "bắt đầu giai đoạn ") + phaseNo + " (" + phase.getPhaseName() + ") ngày " + (early ? today : oldStart)
                        + " | phác đồ " + protocol.getProtocolCode() + (note.isEmpty() ? "" : " | " + note));
        return mapper.toDTO(protocol, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO complete(Integer protocolId, ProtocolTextRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        // Phác đồ đang tạm dừng vì chưa thanh toán (đã cấp thuốc) vẫn được bác sĩ kết thúc
        if (!"IN_TREATMENT".equals(protocol.getState()) && !com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ kết thúc được phác đồ đang điều trị (IN_TREATMENT) hoặc đang tạm dừng do chưa thanh toán. Hiện tại: " + protocol.getState() + ".");
        }
        Set<Integer> finished = finishedPhases(protocolId);
        for (ProtocolPhase phase : protocol.getPhases()) {
            if (!finished.contains(phase.getPhaseNo())) {
                throw new IllegalArgumentException("Phải hoàn thành giai đoạn " + phase.getPhaseNo() + " (" + phase.getPhaseName() + ") trước khi kết thúc phác đồ.");
            }
        }
        // Giai đoạn chưa bắt đầu thì không thu tiền nữa
        phaseBilling.cancelOpenPhaseInvoices(protocol, true, "Phác đồ " + protocol.getProtocolCode() + " kết thúc sớm");
        // Thuốc bổ sung chưa cấp: trả hàng đang giữ và hủy hóa đơn bổ sung chưa thanh toán của thuốc đó
        java.util.Map<Integer, Integer> lineItem = new java.util.HashMap<>();
        protocol.getPhases().forEach(ph -> ph.getItems().forEach(it -> lineItem.put(it.getProtocolItemId(), it.getItem().getItemId())));
        for (StockHold h : holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("HELD", "SHORT"))) {
            phaseBilling.cancelSupplementInvoice(protocolId, h.getItem().getItemId(), lineItem::get,
                    "Phác đồ " + protocol.getProtocolCode() + " kết thúc, thuốc bổ sung chưa cấp");
        }
        protocolStockService.releaseHolds(protocolId, "Trả hàng thuốc bổ sung chưa cấp do phác đồ " + protocol.getProtocolCode() + " kết thúc");
        protocol.setState("COMPLETED");
        protocol.setOutcome(request.getContent().trim());
        protocol.setClosedAt(LocalDateTime.now());
        return mapper.toDTO(protocolRepository.save(protocol), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO cancel(Integer protocolId, ProtocolTextRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!REVISABLE_STATES.contains(protocol.getState()) || alreadyDispensed(protocol)) {
            throw new IllegalArgumentException("Chỉ hủy được phác đồ chưa cấp thuốc. Hiện tại: " + protocol.getState() + ".");
        }
        protocolStockService.releaseHolds(protocolId, "Trả hàng do hủy phác đồ " + protocol.getProtocolCode());
        phaseBilling.refundPaidPhaseInvoices(protocol, request.getContent().trim());
        phaseBilling.cancelOpenPhaseInvoices(protocol, false, "Hủy phác đồ " + protocol.getProtocolCode());
        protocol.setState("CANCELLED");
        protocol.setClosedAt(LocalDateTime.now());
        protocol.setNote(appendNote(protocol.getNote(), "Hủy: " + request.getContent().trim()));
        return mapper.toDTO(protocolRepository.save(protocol), true);
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private TreatmentProtocol buildProtocol(ProtocolCreateRequestDTO request, TreatmentProtocol previous) {
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));

        Integer caseId = request.getCaseId() != null ? request.getCaseId() : (previous != null ? previous.getCaseId() : null);
        Integer recordId = request.getRecordId() != null ? request.getRecordId() : (previous != null ? previous.getRecordId() : null);
        if (caseId != null && !vetCaseExists(caseId)) {
            throw new ResourceNotFoundException("Không tìm thấy ca khám với ID: " + caseId);
        }
        if (recordId != null && !medicalRecordRepository.existsById(recordId)) {
            throw new ResourceNotFoundException("Không tìm thấy bệnh án với ID: " + recordId);
        }
        // Mỗi ca khám chỉ có một phác đồ (đổi thuốc / liều thì tạo phiên bản mới). Ngựa có ca khám mới (VD: Groom báo sự cố)
        // thì được lập phác đồ mới cho ca đó, kể cả khi phác đồ của ca cũ còn đang điều trị.
        if (previous == null && caseId != null) {
            protocolRepository.findByCaseId(caseId).stream()
                    .filter(p -> !"CANCELLED".equals(p.getState()) && !"SUPERSEDED".equals(p.getState()))
                    .findFirst()
                    .ifPresent(p -> {
                        throw new IllegalArgumentException("Ca khám #" + caseId + " đã có phác đồ " + p.getProtocolCode() + " (" + p.getState()
                                + "). Mỗi ca khám chỉ lập một phác đồ: muốn đổi thuốc hãy tạo phiên bản mới, bệnh mới thì mở ca khám mới.");
                    });
        }

        TreatmentProtocol protocol = TreatmentProtocol.builder()
                .horse(horse)
                .vet(currentUser())
                .caseId(caseId)
                .recordId(recordId)
                .versionNo(previous != null ? previous.getVersionNo() + 1 : 1)
                .replacesProtocol(previous)
                .diagnosis(request.getDiagnosis().trim())
                .state("WAITING_STOCK_CHECK") // Gửi kho kiểm hàng ngay khi lập
                .requiredBy(request.getRequiredBy())
                .isDelayed(false)
                .trainingLocked(Boolean.TRUE.equals(horse.getIsLocked()))
                .note(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null)
                .createdAt(LocalDateTime.now())
                .build();

        int phaseNo = 1;
        for (ProtocolCreateRequestDTO.Phase p : request.getPhases()) {
            ProtocolPhase phase = ProtocolPhase.builder()
                    .phaseNo(phaseNo++)
                    .phaseName(p.getPhaseName().trim())
                    .startDate(p.getStartDate())
                    .durationDays(p.getDurationDays())
                    .goal(p.getGoal())
                    .build();
            Set<Integer> seen = new HashSet<>();
            for (ProtocolCreateRequestDTO.Item i : p.getItems()) {
                if (!seen.add(i.getItemId())) {
                    throw new IllegalArgumentException("Thuốc ID " + i.getItemId() + " bị lặp trong giai đoạn '" + p.getPhaseName() + "'. Hãy gộp thành một dòng.");
                }
                InventoryItem item = itemRepository.findById(i.getItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thuốc/vật tư với ID: " + i.getItemId()));
                // Nhóm vật tư bác sĩ được kê trong phác đồ: thuốc, vaccine, vật tư y tế
                if (!com.horsetrack.equinecare.modules.inventory.service.ItemCategories.isMedical(item.getCategory())) {
                    throw new IllegalArgumentException("'" + item.getItemName() + "' (nhóm " + item.getCategory()
                            + ") không thuộc danh mục thuốc. Chỉ kê được Thuốc, Vaccine hoặc Vật tư y tế.");
                }
                phase.addItem(ProtocolItem.builder()
                        .item(item)
                        .quantity(BigDecimal.valueOf(i.getQuantity()).setScale(2, RoundingMode.HALF_UP).doubleValue())
                        .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                        .dosage(StringUtils.hasText(i.getDosage()) ? i.getDosage().trim() : null)
                        .build());
            }
            protocol.addPhase(phase);
        }
        return protocol;
    }

    private TreatmentProtocol saveAndReload(TreatmentProtocol protocol) {
        TreatmentProtocol saved = protocolRepository.saveAndFlush(protocol);
        // Đọc lại để lấy protocol_code và line_total do DB tự sinh
        entityManager.refresh(saved);
        return saved;
    }

    /**
     * BR_17: bác sĩ chọn "Trạng thái cấp cứu" khi lập phác đồ → ca khám chuyển mức CRITICAL. Phác đồ của ca CRITICAL được coi là
     * cấp cứu (ProtocolPhaseBilling.isEmergency): kho cấp phát ngay, không chờ thanh toán, cấp xong chuyển thẳng "Đang điều trị".
     * Bỏ chọn không hạ mức ca khám.
     */
    private void markEmergency(ProtocolCreateRequestDTO request, TreatmentProtocol previous) {
        if (!Boolean.TRUE.equals(request.getEmergency())) {
            return;
        }
        Integer caseId = request.getCaseId() != null ? request.getCaseId() : (previous != null ? previous.getCaseId() : null);
        if (caseId == null) {
            throw new IllegalArgumentException("Phác đồ cấp cứu phải gắn với ca khám (caseId) để ghi nhận mức cấp cứu.");
        }
        // Sửa qua entity (không dùng native UPDATE) để ProtocolPhaseBilling.isEmergency đọc đúng mức mới trong cùng transaction
        com.horsetrack.equinecare.modules.medical.entity.VetCase vetCase = entityManager.find(com.horsetrack.equinecare.modules.medical.entity.VetCase.class, caseId);
        if (vetCase == null) {
            throw new ResourceNotFoundException("Không tìm thấy ca khám với ID: " + caseId);
        }
        String old = vetCase.getSeverity();
        if (!"CRITICAL".equalsIgnoreCase(old)) {
            vetCase.setSeverity("CRITICAL");
            auditTrail.record("MARK_EMERGENCY", "Vet_Cases", "severity=" + old,
                    "severity=CRITICAL | ca khám #" + caseId + " | bác sĩ chọn trạng thái cấp cứu khi lập phác đồ (BR_17)");
        }
    }

    private boolean vetCaseExists(Integer caseId) {
        Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM Vet_Cases WHERE case_id = :id")
                .setParameter("id", caseId)
                .getSingleResult();
        return count.intValue() > 0;
    }

    private LocalDate latestShortageEta(Integer protocolId) {
        return holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("SHORT")).stream()
                .map(StockHold::getEta)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private TreatmentProtocol findProtocol(Integer protocolId) {
        return protocolRepository.findById(protocolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phác đồ với ID: " + protocolId));
    }

    private Set<Integer> finishedPhases(Integer protocolId) {
        return phaseBilling.finishedPhaseNos(protocolId);
    }

    /** Giai đoạn đã cấp mà ngày bắt đầu còn ở tương lai vẫn hoàn thành được: kéo về hôm nay. Giai đoạn đang chạy thì rút ngày kết thúc về hôm nay. */
    private static boolean shortenPhaseToToday(ProtocolPhase phase, LocalDate today) {
        int days = Math.max(1, phase.getDurationDays() == null ? 1 : phase.getDurationDays());
        if (phase.getStartDate().isAfter(today)) {
            phase.setStartDate(today);
            phase.setDurationDays(1);
            return true;
        }
        LocalDate plannedEnd = phase.getStartDate().plusDays(days - 1L);
        if (plannedEnd.isAfter(today)) {
            phase.setDurationDays((int) ChronoUnit.DAYS.between(phase.getStartDate(), today) + 1);
            return true;
        }
        return false;
    }

    private void closeAfterPhase(TreatmentProtocol protocol, int finishedThrough, String outcome) {
        String reason = outcome == null || outcome.isBlank() ? "Phác đồ " + protocol.getProtocolCode() + " kết thúc sau giai đoạn " + finishedThrough : outcome;
        phaseBilling.closePhasesAfter(protocol, finishedThrough, reason);
        java.util.Map<Integer, Integer> lineItem = new java.util.HashMap<>();
        protocol.getPhases().forEach(ph -> ph.getItems().forEach(it -> lineItem.put(it.getProtocolItemId(), it.getItem().getItemId())));
        for (StockHold hold : holdRepository.findByProtocolIdAndStatusIn(protocol.getProtocolId(), List.of("HELD", "SHORT", "PENDING_REVIEW"))) {
            phaseBilling.cancelSupplementInvoice(protocol.getProtocolId(), hold.getItem().getItemId(), lineItem::get, reason);
        }
        protocolStockService.releaseHolds(protocol.getProtocolId(), "Trả hàng chưa cấp do phác đồ " + protocol.getProtocolCode() + " kết thúc sau giai đoạn " + finishedThrough);
        protocol.setState("COMPLETED");
        protocol.setOutcome(outcome);
        protocol.setClosedAt(LocalDateTime.now());
        protocolRepository.save(protocol);
    }

    private void writeNote(TreatmentProtocol protocol, String content) {
        noteRepository.saveAndFlush(ProtocolProgressNote.builder()
                .protocol(protocol)
                .writtenBy(currentUser())
                .writtenAt(LocalDateTime.now())
                .content(content)
                .build());
    }



  
