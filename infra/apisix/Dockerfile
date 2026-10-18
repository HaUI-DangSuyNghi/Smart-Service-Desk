FROM apache/apisix:3.11.0-redhat@sha256:d3c86dc739ade96bdcd0acaee0ab36b846a3ea2ce21ecf78c897d15500d19db0
RUN rpm_arch="$(rpm --eval '%{_arch}')" \
    && case "$rpm_arch" in \
        x86_64) unbound_sha=f1544e0e933245e1b32dc8161fbb97b88883ddef2617039087ff3213ea2b453b; \
            python_sha=ce863671ec0e43199c657c74f6ae9871d6f8e18c5ac246e800359b940fe92f94 ;; \
        aarch64) unbound_sha=de3cbf06ca40d8c6f3bb0d92a102f5a9bf932f5ee837f7863c79e2c791d9d08f; \
            python_sha=7dc7ded57b383dc29e0ea0ab028f9c6fcd543aad07e0a6f3c7bb87a4c66357ca ;; \
        *) echo "Unsupported RPM architecture: $rpm_arch" >&2; exit 1 ;; \
    esac \
    && wget -q -O /tmp/unbound-libs.rpm "https://cdn-ubi.redhat.com/content/public/ubi/dist/ubi8/8/$rpm_arch/appstream/os/Packages/u/unbound-libs-1.16.2-5.14.el8_10.4.$rpm_arch.rpm" \
    && wget -q -O /tmp/python3-unbound.rpm "https://cdn-ubi.redhat.com/content/public/ubi/dist/ubi8/8/$rpm_arch/appstream/os/Packages/p/python3-unbound-1.16.2-5.14.el8_10.4.$rpm_arch.rpm" \
    && printf '%s  %s\n' "$unbound_sha" /tmp/unbound-libs.rpm "$python_sha" /tmp/python3-unbound.rpm | sha256sum -c - \
    && yum --disablerepo='*' install -y /tmp/unbound-libs.rpm /tmp/python3-unbound.rpm \
    && yum clean all \
    && rm -f /tmp/unbound-libs.rpm /tmp/python3-unbound.rpm
