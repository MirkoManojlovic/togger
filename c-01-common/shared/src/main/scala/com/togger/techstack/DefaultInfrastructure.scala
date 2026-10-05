package com.togger.techstack

import enumeratum._

sealed abstract class DefaultInfrastructure(val i: Int, val infrastructureName: String, val description: String)
    extends EnumEntry

object DefaultInfrastructure extends Enum[DefaultInfrastructure] {
  case object Docker
      extends DefaultInfrastructure(0, "Docker", "Platform for building and running containerized applications")
  case object Kubernetes
      extends DefaultInfrastructure(1, "Kubernetes", "Container orchestration platform for automating deployment and scaling")
  case object Linux extends DefaultInfrastructure(2, "Linux", "Open-source Unix-like operating system kernel")
  case object WindowsServer
      extends DefaultInfrastructure(3, "Windows Server", "Microsoft's server operating system")
  case object MacOs extends DefaultInfrastructure(4, "macOS", "Apple's desktop/server operating system")
  case object Aws
      extends DefaultInfrastructure(5, "AWS", "Amazon Web Services cloud computing platform")
  case object Azure extends DefaultInfrastructure(6, "Azure", "Microsoft's cloud computing platform")
  case object GoogleCloudPlatform
      extends DefaultInfrastructure(7, "Google Cloud Platform", "Google's cloud computing platform")
  case object Terraform
      extends DefaultInfrastructure(8, "Terraform", "Infrastructure-as-code tool for provisioning cloud resources")
  case object Ansible
      extends DefaultInfrastructure(9, "Ansible", "Agentless configuration management and automation tool")
  case object Puppet extends DefaultInfrastructure(10, "Puppet", "Configuration management tool")
  case object Chef extends DefaultInfrastructure(11, "Chef", "Configuration management and automation tool")
  case object Jenkins extends DefaultInfrastructure(12, "Jenkins", "Automation server for CI/CD pipelines")
  case object GithubActions
      extends DefaultInfrastructure(13, "GitHub Actions", "CI/CD automation built into GitHub")
  case object GitlabCi extends DefaultInfrastructure(14, "GitLab CI", "CI/CD automation built into GitLab")
  case object Nginx extends DefaultInfrastructure(15, "Nginx", "Web server, reverse proxy and load balancer")
  case object Apache
      extends DefaultInfrastructure(16, "Apache HTTP Server", "Open-source web server software")
  case object Helm extends DefaultInfrastructure(17, "Helm", "Package manager for Kubernetes")
  case object Vagrant
      extends DefaultInfrastructure(18, "Vagrant", "Tool for building and managing virtual machine environments")
  case object VMware extends DefaultInfrastructure(19, "VMware", "Virtualization and cloud infrastructure software")
  case object Istio extends DefaultInfrastructure(20, "Istio", "Service mesh for microservices networking and security")
  case object Prometheus
      extends DefaultInfrastructure(21, "Prometheus", "Monitoring and alerting toolkit")
  case object Grafana extends DefaultInfrastructure(22, "Grafana", "Observability and metrics visualization platform")

  override def values: IndexedSeq[DefaultInfrastructure] = findValues
}
