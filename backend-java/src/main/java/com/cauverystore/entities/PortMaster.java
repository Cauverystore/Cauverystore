package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * Indian port, ICD and SEZ codes.
 *
 * Required on an export e-invoice as the port of despatch. The list covers inland container
 * depots and SEZ units as well as seaports, which is why it runs to over a thousand entries -
 * guessing one is not realistic, and the IRP rejects anything not on it.
 */
@Entity
@Table(name = "port_master")
public class PortMaster {

    @Id
    @Column(name = "port_code", length = 10, nullable = false)
    private String portCode;

    @Column(name = "port_name", nullable = false)
    private String portName;

    public PortMaster() {
    }

    public PortMaster(String portCode, String portName) {
        this.portCode = portCode;
        this.portName = portName;
    }

    public String getPortCode() { return portCode; }
    public void setPortCode(String portCode) { this.portCode = portCode; }
    public String getPortName() { return portName; }
    public void setPortName(String portName) { this.portName = portName; }
}
