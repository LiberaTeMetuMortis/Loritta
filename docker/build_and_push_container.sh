# $1 = Image Name
# $2 = Folder
IMAGE_NAME=$1

echo "Building $IMAGE_NAME by $GITHUB_REPO_OWNER... Folder: $2"

# Run setup
sh $2/config.sh "$2"

# Build container
docker build $2 --file $2/Dockerfile --tag $IMAGE_NAME --label "runnumber=${GITHUB_RUN_ID}"

IMAGE_ID=ghcr.io/$GITHUB_REPO_OWNER/$IMAGE_NAME

# Change all uppercase to lowercase
IMAGE_ID=$(echo $IMAGE_ID | tr '[A-Z]' '[a-z]')

# Strip git ref prefix from version
VERSION=$(echo "$GITHUB_REF" | sed -e 's,.*/\(.*\),\1,')

# Strip "v" prefix from tag name
[[ "$GITHUB_REF" == "refs/tags/"* ]] && VERSION=$(echo $VERSION | sed -e 's/^v//')

# Use Docker `latest` tag convention
[ "$VERSION" == "master" ] && VERSION=latest
[ "$VERSION" == "main" ] && VERSION=latest

echo IMAGE_ID=$IMAGE_ID
echo VERSION=$VERSION

docker tag $IMAGE_NAME $IMAGE_ID:$VERSION
docker push $IMAGE_ID:$VERSION