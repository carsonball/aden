package org.carball.aden.terraform.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerraformOutput {
    private String mainTf;
    private String variablesTf;
    private String outputsTf;
    
    public boolean isEmpty() {
        return (mainTf == null || mainTf.isEmpty()) &&
               (variablesTf == null || variablesTf.isEmpty()) &&
               (outputsTf == null || outputsTf.isEmpty());
    }
}