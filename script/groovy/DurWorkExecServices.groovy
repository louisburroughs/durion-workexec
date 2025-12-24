package durion.workexec

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import java.math.BigDecimal
import java.math.RoundingMode

class DurWorkExecServices {

    /**
     * Calculate and update work order totals
     */
    static void calculateWorkOrderTotals(ExecutionContext ec, String workOrderId) {
        def items = ec.entity.find("durion.workexec.DurWorkOrderItem")
                .condition("workOrderId", workOrderId)
                .list()
        
        BigDecimal totalLabor = BigDecimal.ZERO
        BigDecimal totalParts = BigDecimal.ZERO
        
        items.each { item ->
            BigDecimal lineTotal = (item.quantity ?: BigDecimal.ONE) * (item.unitPrice ?: BigDecimal.ZERO)
            
            if (item.itemType == 'LABOR' || item.itemType == 'SERVICE') {
                totalLabor += lineTotal
            } else if (item.itemType == 'PART') {
                totalParts += lineTotal
            }
        }
        
        BigDecimal subtotal = totalLabor + totalParts
        BigDecimal taxRate = new BigDecimal("0.08") // 8% tax rate
        BigDecimal totalTax = subtotal * taxRate
        totalTax = totalTax.setScale(2, RoundingMode.HALF_UP)
        BigDecimal totalAmount = subtotal + totalTax
        
        ec.service.sync().name("update", "durion.workexec.DurWorkOrder")
                .parameters([workOrderId: workOrderId, 
                            totalLabor: totalLabor, 
                            totalParts: totalParts, 
                            totalTax: totalTax, 
                            totalAmount: totalAmount])
                .call()
    }

    /**
     * Calculate and update estimate totals
     */
    static void calculateEstimateTotals(ExecutionContext ec, String estimateId) {
        def items = ec.entity.find("durion.workexec.DurEstimateItem")
                .condition("estimateId", estimateId)
                .list()
        
        BigDecimal laborAmount = BigDecimal.ZERO
        BigDecimal partsAmount = BigDecimal.ZERO
        
        items.each { item ->
            BigDecimal lineTotal = (item.quantity ?: BigDecimal.ONE) * (item.unitPrice ?: BigDecimal.ZERO)
            
            if (item.itemType == 'LABOR' || item.itemType == 'SERVICE') {
                laborAmount += lineTotal
            } else if (item.itemType == 'PART') {
                partsAmount += lineTotal
            }
        }
        
        BigDecimal subtotal = laborAmount + partsAmount
        BigDecimal taxRate = new BigDecimal("0.08")
        BigDecimal taxAmount = subtotal * taxRate
        taxAmount = taxAmount.setScale(2, RoundingMode.HALF_UP)
        BigDecimal totalAmount = subtotal + taxAmount
        
        ec.service.sync().name("update", "durion.workexec.DurEstimate")
                .parameters([estimateId: estimateId, 
                            laborAmount: laborAmount, 
                            partsAmount: partsAmount, 
                            taxAmount: taxAmount, 
                            totalAmount: totalAmount])
                .call()
    }

    /**
     * Find available mobile units for roadside dispatch
     */
    static Map findAvailableMobileUnits(ExecutionContext ec, BigDecimal latitude, BigDecimal longitude, Integer maxDistance) {
        def availableUnits = []
        
        def units = ec.entity.find("durion.workexec.DurMobileUnit")
                .condition("statusId", "MU_AVAILABLE")
                .list()
        
        units.each { unit ->
            if (unit.currentLatitude && unit.currentLongitude) {
                Double distance = calculateDistance(
                    latitude.doubleValue(), longitude.doubleValue(),
                    unit.currentLatitude.doubleValue(), unit.currentLongitude.doubleValue()
                )
                
                if (maxDistance == null || distance <= maxDistance) {
                    availableUnits.add([
                        mobileUnitId: unit.mobileUnitId,
                        unitNumber: unit.unitNumber,
                        unitType: unit.unitType,
                        currentMechanicId: unit.currentMechanicId,
                        distance: distance
                    ])
                }
            }
        }
        
        // Sort by distance
        availableUnits.sort { it.distance }
        
        return [availableUnits: availableUnits]
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     */
    private static Double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        final Integer R = 6371 // Radius of Earth in kilometers
        
        Double latDistance = Math.toRadians(lat2 - lat1)
        Double lonDistance = Math.toRadians(lon2 - lon1)
        
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return R * c // Distance in kilometers
    }

    /**
     * Check if warranty is still valid based on original work order date and mileage
     */
    static Map checkWarrantyValidity(ExecutionContext ec, String originalWorkOrderId, Integer currentMileage) {
        def workOrder = ec.entity.find("durion.workexec.DurWorkOrder")
                .condition("workOrderId", originalWorkOrderId)
                .one()
        
        if (!workOrder) {
            return [isValid: false, reason: "Original work order not found"]
        }
        
        // Check time-based warranty (90 days)
        Long daysSinceWork = (ec.user.nowTimestamp.time - workOrder.orderDate.time) / (1000 * 60 * 60 * 24)
        if (daysSinceWork > 90) {
            return [isValid: false, reason: "Warranty expired - more than 90 days since original work"]
        }
        
        // Check mileage-based warranty (3000 miles)
        if (currentMileage && workOrder.mileageIn) {
            Integer milesDriven = currentMileage - workOrder.mileageIn
            if (milesDriven > 3000) {
                return [isValid: false, reason: "Warranty expired - more than 3000 miles driven"]
            }
        }
        
        return [isValid: true, daysSinceWork: daysSinceWork, originalWorkDate: workOrder.orderDate]
    }

    /**
     * Calculate warranty case statistics for reporting
     */
    static Map getWarrantyCaseStatistics(ExecutionContext ec, java.sql.Date fromDate, java.sql.Date toDate) {
        def cases = ec.entity.find("durion.workexec.DurWarrantyCase")
                .condition("claimDate", ">=", fromDate)
                .condition("claimDate", "<=", toDate)
                .list()
        
        Integer totalCases = cases.size()
        Integer approvedCases = 0
        Integer deniedCases = 0
        BigDecimal totalClaimAmount = BigDecimal.ZERO
        BigDecimal totalApprovedAmount = BigDecimal.ZERO
        
        cases.each { wCase ->
            totalClaimAmount += wCase.claimAmount ?: BigDecimal.ZERO
            
            if (wCase.statusId == 'WC_APPROVED' || wCase.statusId == 'WC_PAID') {
                approvedCases++
                totalApprovedAmount += wCase.approvedAmount ?: BigDecimal.ZERO
            } else if (wCase.statusId == 'WC_DENIED') {
                deniedCases++
            }
        }
        
        BigDecimal approvalRate = totalCases > 0 ? 
            (approvedCases / totalCases * 100).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO
        
        return [
            totalCases: totalCases,
            approvedCases: approvedCases,
            deniedCases: deniedCases,
            pendingCases: totalCases - approvedCases - deniedCases,
            totalClaimAmount: totalClaimAmount,
            totalApprovedAmount: totalApprovedAmount,
            approvalRate: approvalRate
        ]
    }
}
