package com.lhr.rnd.service;

import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.SampleProject;
import com.lhr.rnd.model.SampleVersion;

public record ApproveSampleRequestResult(SampleProject project, SampleVersion version, RndTask task) {
}
