package com.atguigu.gmall.task.contorller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.task.scheduled.ScheduledTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task")
public class TaskController {
    @Autowired
    ScheduledTask scheduledTask;

    @GetMapping("task1")
    public Result task1() {
        scheduledTask.task1();
        return Result.ok();
    }

    @GetMapping("task18")
    public Result task18() {
        scheduledTask.task18();
        return Result.ok();
    }
}
