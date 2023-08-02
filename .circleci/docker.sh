#!/bin/bash
set -euxo pipefail

build_type=${1}

if [ "${build_type}" == "circle" ] ; then
    tag1=circle
    tag2=$(git rev-parse HEAD)
elif [ "${build_type}" == "test" ] ; then
    tag1=test
    tag2=$(git describe --tags --exact-match)
elif [ "${build_type}" == "stage" ] ; then
    tag1=stage
    tag2=$(git describe --tags --exact-match)
elif [ "${build_type}" == "release" ] ; then
    tag1=release
    tag2=$(git describe --tags --exact-match)
else
    echo "ERROR: Only \"circle\", \"test\", \"stage\" and \"release\" arguments supported!"
    echo "USAGE: ${0} [ circle | release | stage | test ]"
    exit 1
fi

docker build --pull \
    --tag ada/rems:latest \
    --tag ada/rems:${tag2} \
    --tag docker-registry.cadre-rems.ada.edu.au:5000/ada/rems:${tag1} \
    --tag docker-registry.cadre-rems.ada.edu.au:5000/ada/rems:${tag2} .

docker login -p $dockerregpwd -u docker docker-registry.cadre-rems.ada.edu.au:5000
docker push docker-registry.cadre-rems.ada.edu.au:5000/ada/rems:${tag1}
docker push docker-registry.cadre-rems.ada.edu.au:5000/ada/rems:${tag2}
