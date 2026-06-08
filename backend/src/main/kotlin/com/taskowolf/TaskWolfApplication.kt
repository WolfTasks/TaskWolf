package com.taskowolf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class TaskWolfApplication

fun main(args: Array<String>) {
    runApplication<TaskWolfApplication>(*args)
}
